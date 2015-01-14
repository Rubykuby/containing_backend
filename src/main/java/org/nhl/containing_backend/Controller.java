package org.nhl.containing_backend;

import org.nhl.containing_backend.communication.*;
import org.nhl.containing_backend.communication.messages.ArriveMessage;
import org.nhl.containing_backend.communication.messages.CreateMessage;
import org.nhl.containing_backend.communication.messages.Message;
import org.nhl.containing_backend.communication.messages.SpeedMessage;
import org.nhl.containing_backend.models.Container;
import org.nhl.containing_backend.models.Model;
import org.nhl.containing_backend.vehicles.Transporter;
import org.nhl.containing_backend.xml.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.io.StringReader;
import java.util.*;
import java.util.List;
import org.nhl.containing_backend.communication.messages.CraneMessage;
import org.nhl.containing_backend.communication.messages.DepartMessage;
import org.nhl.containing_backend.communication.messages.MoveMessage;
import org.nhl.containing_backend.cranes.Crane;
import org.nhl.containing_backend.models.Storage;
import org.nhl.containing_backend.vehicles.Agv;

/**
 * Main controller class.
 */
public class Controller implements Runnable {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    private float speed;
    private boolean running;
    private Server server;
    private Date currentDate;
    private long lastTime;
    private long sumTime = Integer.MAX_VALUE;
    private long updateSpeedTime = Integer.MAX_VALUE;
    private Calendar cal;
    private Database database;
    private Model model;
    private List<Message> messagePool;
    private List<Message> arriveMessagesList;
    private List<Message> craneMessageList;
    private List<Message> moveMessagesList;
    private List<Container> containerListStorage;
    private List<Integer> tempCraneIDS;

    public Controller() {
        speed = 1;
        server = new Server();
        model = new Model();
        messagePool = new ArrayList<>();
        arriveMessagesList = new ArrayList<>();
        moveMessagesList = new ArrayList<>();
        containerListStorage = new ArrayList<>();
        tempCraneIDS = new ArrayList<>();
        craneMessageList = new ArrayList<>();
        database = new Database(model);
        running = false;
    }

    /**
     * Starts the controller and all the necessary initialisations.
     */
    public void start() {
        model.getContainerPool().addAll(createContainersFromXmlResource());
        startServer();
        waitForServerConnection();
        initDate(); // Keep this as CLOSE to `while (running)` as possible.
        updateSpeed(speed);
        running = true;
        while (running) {
            if (!server.isRunning()) {
                return;
            }
            updateDate();

            if (sumTime > 1000) {
                spawnTransporters();
                sumTime = 0;
            }
            if (updateSpeedTime > 10000) {
                updateSpeed(speed);
                updateSpeedTime = 0;
            }
            assignTransportersToDepots();

            handleOkMessages();

            try {
                Thread.sleep(50);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shuts down the server and sets running to false.
     */
    public void stop() {
        server.stop();
        running = false;
    }

    public void updateSpeed(float speed) {
        SpeedMessage message = new SpeedMessage(speed, currentDate.toString());
        messagePool.add(message);
        server.writeMessage(message.generateXml());
    }

    /**
     * Initialises the simulation date.
     */
    private void initDate() {
        cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2004);
        cal.set(Calendar.MONTH, 11);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        currentDate = cal.getTime();
        lastTime = System.currentTimeMillis();
    }

    /**
     * Updates the simulation date.
     * <p/>
     * Compares the time since the last function call to the current time. This
     * is the delta time. The delta time is added to the simulation date,
     * multiplied by the specified TIME_MULTIPLIER.
     */
    private void updateDate() {
        long curTime = System.currentTimeMillis();
        int deltaTime = (int) (curTime - lastTime);
        sumTime += deltaTime;
        updateSpeedTime += deltaTime;
        cal.add(Calendar.MILLISECOND, (int) (deltaTime * speed));
        currentDate = cal.getTime();
        lastTime = curTime;
    }

    /**
     * Starts up the server in a separate thread.
     */
    private void startServer() {
        Thread serverThread = new Thread(server);
        serverThread.start();
    }

    /**
     * Halts the program until the server has connected to a client.
     */
    private void waitForServerConnection() {
        while (true) {
            if (server.isRunning()) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return a list of all containers described in the XML files.
     */
    private List<Container> createContainersFromXmlResource() {
        List<Container> containers = new ArrayList<>();
        try {
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml1.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml2.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml3.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml4.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml5.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml6.xml")));
            containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml7.xml")));
            //containers.addAll(Xml.parseContainerXml(Controller.class.getResourceAsStream("/xml8.xml")));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return containers;
    }

    /**
     * Pops the containers from the pool that are set to be dispatched for the
     * current date.
     */
    private List<Container> containersForCurrentDate() {
        List<Container> result = new ArrayList<>();

        Iterator<Container> i = model.getContainerPool().iterator();
        while (i.hasNext()) {
            Container container = i.next();
            Date arrivalDate = container.getArrivalDate();
            if (arrivalDate.before(currentDate)) {
                result.add(container);
                i.remove();
            }
        }

        return result;
    }

    /**
     * Spawns new transporters if the time is right.
     */
    private void spawnTransporters() {
        List<Container> containers = containersForCurrentDate();

        if (containers.isEmpty()) {
            return;
        }

        List<Transporter> transporters = distributeContainers(containers);
        model.getTransporters().addAll(transporters);

        for (Transporter transporter : transporters) {
            CreateMessage message = new CreateMessage(transporter);
            messagePool.add(message);
            transporter.setProcessingMessageId(message.getId());
            server.writeMessage(message.generateXml());
        }
    }

    /**
     * Distributes the provided containers over a list of newly generated
     * transporters.
     * </p>
     * WARNING: Method is butt-ugly.
     *
     * @param containers Containers that have to arrive in harbour.
     * @return Transporters loaded with containers that are ready to arrive.
     */
    private List<Transporter> distributeContainers(List<Container> containers) {
        List<Container> lorryContainers = new ArrayList<>();
        List<Container> trainContainers = new ArrayList<>();
        List<Container> inlandshipContainers = new ArrayList<>();
        List<Container> seashipContainers = new ArrayList<>();

        String[] types = new String[]{"vrachtauto", "trein", "binnenschip", "zeeschip"};

        // Create separate lists for all transporter types.
        for (Container container : containers) {
            if (container.getArrivalTransportType().equals(types[0])) {
                lorryContainers.add(container);
            } else if (container.getArrivalTransportType().equals(types[1])) {
                trainContainers.add(container);
            } else if (container.getArrivalTransportType().equals(types[2])) {
                inlandshipContainers.add(container);
            } else if (container.getArrivalTransportType().equals(types[3])) {
                seashipContainers.add(container);
            }
        }

        List<List<Container>> listOfLists = new ArrayList<>();
        listOfLists.add(lorryContainers);
        listOfLists.add(trainContainers);
        listOfLists.add(inlandshipContainers);
        listOfLists.add(seashipContainers);

        List<Transporter> result = new ArrayList<>();

        int counter = 0;

        // Loop over all the lists.
        for (List<Container> containers_ : listOfLists) {
            String type = types[counter];

            if (containers_.isEmpty()) {
                counter++;
                continue;
            }

            // Create a dictionary where a key is a (x, y, z) coordinate, and the value is a list of containers.
            Map<String, List<Container>> dict = new HashMap<>();

            // Distribute the containers over the dictionary.
            for (Container container : containers_) {
                String point = "";
                point += container.getSpawnX() + ",";
                point += container.getSpawnY() + ",";
                point += container.getSpawnZ();

                if (!dict.containsKey(point)) {
                    List<Container> pointList = new ArrayList<>();
                    pointList.add(container);
                    dict.put(point, pointList);
                } else {
                    dict.get(point).add(container);
                }
            }

            // Figure out the biggest list in the dictionary. This is also the amount of transporters.
            int amountOfTransporters = 0;
            for (List<Container> containerList : dict.values()) {
                if (amountOfTransporters < containerList.size()) {
                    amountOfTransporters = containerList.size();
                }
            }

            // Figure out the boundaries of the transporters.
            int limitX = 1;
            int limitY = 1;
            int limitZ = 1;
            for (String point : dict.keySet()) {
                String[] coords = point.split(",");
                if (limitX < Integer.parseInt(coords[0]) + 1) {
                    limitX = Integer.parseInt(coords[0]) + 1;
                }
                if (limitY < Integer.parseInt(coords[1]) + 1) {
                    limitY = Integer.parseInt(coords[1]) + 1;
                }
                if (limitZ < Integer.parseInt(coords[2]) + 1) {
                    limitZ = Integer.parseInt(coords[2]) + 1;
                }
            }

            // For every transporter, fill it with containers and add it to the list.
            for (int i = 0; i < amountOfTransporters; i++) {
                Transporter transporter = new Transporter(type, limitX, limitY, limitZ);

                Iterator iterator = dict.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, List<Container>> pair = (Map.Entry<String, List<Container>>) iterator.next();
                    Point point = new Point(Integer.parseInt(pair.getKey().split(",")[0]),
                            Integer.parseInt(pair.getKey().split(",")[1]));
                    Container container = null;
                    try {
                        container = pair.getValue().remove(0);
                    } catch (IndexOutOfBoundsException e) {
                        iterator.remove();
                        continue;
                    }
                    transporter.putContainer(point, container);
                }
                if (transporter.getContainers().size() > 0) {
                    result.add(transporter);
                }
            }

            counter++;
        }

        return result;
    }

    /**
     * From the pool of transporters that are currently out of bounds, assign as
     * much of them as possible to a depot that isn't currently being used.
     */
    private void assignTransportersToDepots() {
        HashMap<String, List<Integer>> availableDepots = model.availableDepots();
        for (Transporter transporter : model.getTransporters()) {
            // If the transporter is doing nothing, has finished processing its message, and there is a free depot.
            if (!transporter.isOccupied() && transporter.getProcessingMessageId() == -1
                    && !availableDepots.get(transporter.getType()).isEmpty()) {
                // In the array of depots for the type of the transporter, set the first available depot to the current
                // transporter.
                int spot = availableDepots.get(transporter.getType()).remove(0);
                model.getDepots().get(transporter.getType())[spot] = transporter;
                transporter.setOccupied(true);

                ArriveMessage message = new ArriveMessage(transporter, spot);
                //Database update
                database.updateDatabaseTransporters(transporter);
                messagePool.add(message);
                transporter.setProcessingMessageId(message.getId());
                server.writeMessage(message.generateXml());
            }
        }
    }

    private void moveCranes(Message message) {
        boolean containerOfAgv = checkforContainer(message);
        if (containerOfAgv == false) {
            moveContainerToAgv(message);
        } else {
            moveContainerFromAgv(message);
        }
    }

    /**
     * Agv needs to get a container
     *
     * @param message
     */
    private void moveContainerToAgv(Message message) {
        MoveMessage moveMessage = (MoveMessage) message;
        ArriveMessage arriveMessage = null;
        Iterator<Message> i = arriveMessagesList.iterator();
        while (i.hasNext()) {
            arriveMessage = (ArriveMessage) i.next();
            String dijkie;
            Crane crane;
            crane = findCrane("vrachtauto", 1);
            dijkie = getDijkstraPath(moveMessage.getAgv(), crane);
            String firstChar = dijkie.substring(0);
            boolean found = false;
            Crane craneTransporter;
            Transporter transporter;
            Agv agv;
            Container container;
            Storage storage;

            for (int p = 0; p < tempCraneIDS.size(); p++) {
                Crane tempCrane = findCrane(arriveMessage.getTransporter().getType(), tempCraneIDS.get(p));
                if (tempCrane != null) {
                    tempCrane.setOccupied(false);
                    tempCraneIDS.remove(p);
                    break;
                }
            }


            try {
                Thread.sleep(250);
                int numberOfContainers;
                switch (firstChar) {
                    case "P": {
                        //SchepenOpslag Trein kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageSchipNorth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    case "Q": {
                        //SchepenOpslag vrachtauto kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageSchipSouth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    case "O": {
                        //TreinOpslag Trein kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageTreinpNorth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    case "N": {
                        //TreinOpslag vrachtauto kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageTrainSouth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    case "L": {
                        //VrachtautoOpslag Trein kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageVrachtautoNorth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    case "M": {
                        //VrachtautoOpslag vrachtauto kant
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        craneTransporter = findAvailableCrane("StorageVrachtautoSouth");
                        transporter = null;
                        agv = findAgv(arriveMessage);
                        container = findContainer(containerListStorage);
                        storage = model.getStorage();
                        CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                        craneTransporter.setOccupied(true);
                        database.updateDatabaseStorage(storage);
                        craneMessageList.add(message);
                        messagePool.add(craneMSG);
                        agv.attachContainer(container);
                        agv.setProcessingMessageId(craneMSG.getId());
                        agv.setOccupied(true);
                        server.writeMessage(craneMSG.generateXml());
                        break;
                    }
                    default: {
                        numberOfContainers = arriveMessage.getTransporter().getContainers().size() - 1;
                        container = arriveMessage.getTransporter().getContainers().get(numberOfContainers);
                        craneTransporter = findCrane(arriveMessage.getTransporter().getType(), moveMessage.getEndLocationId());
                        Thread.sleep(1000);
                        if (craneTransporter != null) {
                            found = true;
                            transporter = arriveMessage.getTransporter();
                            agv = findAgv(arriveMessage);
                            storage = null;
                            CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                            craneTransporter.setOccupied(true);
                            updateDatabase(craneTransporter);
                            craneMessageList.add(message);
                            messagePool.add(craneMSG);
                            agv.attachContainer(container);
                            agv.setProcessingMessageId(craneMSG.getId());
                            agv.setOccupied(true);
                            server.writeMessage(craneMSG.generateXml());
                            arriveMessage.getTransporter().getContainers().remove(numberOfContainers);

                            //if (numberOfContainers <= 0) {
                                i.remove();
                            //}
                        }
                        break;
                    }
                }
                if (found) {
                    break;
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Get rid of container of Agv
     *
     * @param message
     */
    private void moveContainerFromAgv(Message message) {
        MoveMessage moveMessage = (MoveMessage) message;
        ArriveMessage arriveMessage = null;
        Iterator<Message> i = arriveMessagesList.iterator();
        while (i.hasNext()) {
            arriveMessage = (ArriveMessage) i.next();
            String dijkie;
            Crane crane;
            crane = findCrane("vrachtauto", 1);
            dijkie = getDijkstraPath(moveMessage.getAgv(), crane);
            String firstChar = dijkie.substring(0);

            Crane craneTransporter;
            Transporter transporter;
            Agv agv;
            Container container;
            Storage storage;
            switch (firstChar) {
                case "P": {
                    //SchepenOpslag Trein kant
                    craneTransporter = findCrane("StorageSchipNorth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                case "Q": {
                    //SchepenOpslag vrachtauto kant
                    craneTransporter = findCrane("StorageSchipSouth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                case "O": {
                    //TreinOpslag Trein kant
                    craneTransporter = findCrane("StorageTreinpNorth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                case "N": {
                    //TreinOpslag vrachtauto kant
                    craneTransporter = findCrane("StorageTrainSouth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                case "L": {
                    //VrachtautoOpslag Trein kant
                    craneTransporter = findCrane("StorageVrachtautoNorth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                case "M": {
                    //VrachtautoOpslag vrachtauto kant
                    craneTransporter = findCrane("StorageVrachtautoSouth", arriveMessage.getDepotIndex());
                    transporter = null;
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = model.getStorage();
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    database.updateDatabaseStorage(storage);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
                default: {
                    craneTransporter = findCrane(arriveMessage.getTransporter().getType(), arriveMessage.getDepotIndex());
                    transporter = arriveMessage.getTransporter();
                    agv = findAgv(message);
                    container = moveMessage.getAgv().getContainer();
                    storage = null;
                    CraneMessage craneMSG = new CraneMessage(craneTransporter, transporter, agv, container, storage);
                    updateDatabase(craneTransporter);
                    messagePool.add(craneMSG);
                    agv.setProcessingMessageId(craneMSG.getId());
                    agv.setOccupied(true);
                    server.writeMessage(craneMSG.generateXml());
                    break;
                }
            }
        }
    }

    private Crane findCrane(String transporttype, int craneId) {
        Crane ret = null;
        switch (transporttype) {
            case "vrachtauto":
            case "TruckCrane":
                for (Crane crane : model.getTruckCranes()) {
                    if (crane.getId() == craneId) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "trein":
            case "TrainCrane":
                for (Crane crane : model.getTrainCranes()) {
                    if (crane.getId() == craneId) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "binnenschip":
            case "DockingCraneInlandShip":
                for (Crane crane : model.getDockingCranesInland()) {
                    if (crane.getId() == craneId) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "zeeschip":
            case "DockingCraneSeaShip":
                for (Crane crane : model.getDockingCranesSea()) {
                    if (crane.getId() == craneId) {
                        ret = crane;
                        break;
                    }
                }
                break;
        }
        if (ret == null) {
            System.out.println(ANSI_RED + "Unable to find crane " + transporttype + " with ID " + craneId + ANSI_RESET);
        }
        return ret;
    }

    private Crane findAvailableCrane(String transporttype) {
        Crane ret = null;
        switch (transporttype) {
            case "vrachtauto":
            case "TruckCrane":
                for (Crane crane : model.getTruckCranes()) {
                    if (!crane.isOccupied()) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "trein":
            case "TrainCrane":
                for (Crane crane : model.getTrainCranes()) {
                    if (!crane.isOccupied()) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "binnenschip":
            case "DockingCraneInlandShip":
                for (Crane crane : model.getDockingCranesInland()) {
                    if (!crane.isOccupied()) {
                        ret = crane;
                        break;
                    }
                }
                break;
            case "zeeschip":
            case "DockingCraneSeaShip":
                for (Crane crane : model.getDockingCranesSea()) {
                    if (!crane.isOccupied()) {
                        ret = crane;
                        break;
                    }
                }
                break;
        }
        if (ret == null) {
            System.out.println(ANSI_RED + "Unable to find available crane " + transporttype + ANSI_RESET);
        }
        return ret;
    }

    private Crane findstoragecrane(String transporttype) {
        if (transporttype.equals("vrachtauto") || transporttype.equals("TruckCrane")) {
            for (Crane crane : model.getStorageCrane()) {
                if (!crane.isOccupied() && crane.getType().equals("vrachtauto")) {
                    return crane;
                }
            }
        }
        if (transporttype.equals("trein") || transporttype.equals("TrainCrane")) {
            for (Crane crane : model.getStorageCrane()) {
                if (!crane.isOccupied() && crane.getType().equals("trein")) {
                    return crane;
                }
            }
        }
        if (transporttype.equals("binnenschip") || transporttype.equals("DockingCraneInlandShip")) {
            for (Crane crane : model.getStorageCrane()) {
                if (!crane.isOccupied() && crane.getType().equals("binnenschip")) {
                    return crane;
                }
            }
        }
        if (transporttype.equals("zeeschip") || transporttype.equals("DockingCraneSeaShip")) {
            for (Crane crane : model.getStorageCrane()) {
                if (!crane.isOccupied()) {
                    return crane;
                }
            }
        }
        System.out.println(ANSI_RED + "Unable to find available storagecrane " + transporttype + ANSI_RESET);
        return null;
    }

    private boolean checkforContainer(Message message) {
        MoveMessage moveMessage = (MoveMessage) message;
        if (moveMessage.getAgv().getContainer() == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Find a Agv
     *
     * @param message
     * @return
     */
    private Agv findAgv(Message message) {
        Agv ret = null;
        String transporter = "";
        if (message.getMessageType() == Message.MOVE) {
            MoveMessage moveMessage = (MoveMessage) message;
            ret = moveMessage.getAgv();
        } else if (message.getMessageType() == Message.ARRIVE) {
            ArriveMessage arriveMessage = (ArriveMessage) message;
            transporter = arriveMessage.getTransporter().getType();
            for (Agv agv : model.getAgvs()) {
                if (!agv.isOccupied() && transporter.equals(agv.getLocationType())) {
                    agv.setOccupied(true);
                    ret = agv;
                    break;
                }
            }
        }
        if (ret == null) {
            System.out.println(ANSI_RED + "Unable to find available agv for transporter " + transporter + ANSI_RESET);
        }
        return ret;
    }

    private Container findContainer(List<Container> containers) {
        Date firstDate = null;
        Container ret = null;
        for (Container container : containers) {
            if (firstDate == null) {
                firstDate = container.getDepartureDate();
            } else if (firstDate.after(container.getDepartureDate())) {
                firstDate = container.getDepartureDate();
            }
        }
        for (Container container : containers) {
            if (container.getDepartureDate().equals(firstDate)) {
                ret = container;
                break;
            }
        }
        if (ret == null) {
            System.out.println(ANSI_RED + "Unable to find available container with date " + firstDate + ANSI_RESET);
        }
        return ret;
    }

    private Container findContainerByNumber(List<Container> containers, int containerNumber) {
        Container ret = null;
        for (Container con : containers) {
            if (con.getNumber() == containerNumber) {
                ret = con;
                break;
            }
        }
        if (ret == null) {
            System.out.println(ANSI_RED + "Unable to find available container with number " + containerNumber + ANSI_RESET);
        }
        return ret;
    }

    /**
     * Departs the transporter when the transporter is empty
     *
     * @param message
     */
    private void departTransporter(Message message) {
        CraneMessage craneMessage = (CraneMessage) message;
        if (craneMessage.getTransporter().getContainers().isEmpty()) {
            DepartMessage departMessage = new DepartMessage(craneMessage.getTransporter());
            messagePool.add(departMessage);
            craneMessage.getTransporter().setProcessingMessageId(departMessage.getId());
            server.writeMessage(departMessage.generateXml());
        }

    }

    /**
     * AGV To Transporter
     *
     * @param message
     */
    private void moveAgvArrive(Message message) {
        if (message.getMessageType() == Message.ARRIVE) {
            ArriveMessage arrivedMessage = (ArriveMessage) message;
            String dijkstra;
            Crane crane;

            for (Agv agv : model.getAgvs()) {
                if (!agv.isOccupied()) {

                    crane = findAvailableCrane(arrivedMessage.getTransporter().getType());

                    dijkstra = getDijkstraPath(agv, crane);
                    agv.setOccupied(true);
                    crane.setOccupied(true);
                    tempCraneIDS.add(crane.getId());
                    try {
                        Thread.sleep(1000);
                        MoveMessage moveMessage = new MoveMessage(agv, dijkstra, crane);
                        messagePool.add(moveMessage);
                        agv.setProcessingMessageId(moveMessage.getId());
                        agv.setLocationType(arrivedMessage.getTransporter().getType());
                        server.writeMessage(moveMessage.generateXml());
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Move AGV to storage
     *
     * @param message
     */
    private void moveAgvCrane(Message message) {
        if (message.getMessageType() == Message.CRANE) {
            String dijkstra;
            Crane crane;

            for (Message getMessage : moveMessagesList) {
                MoveMessage moveToStorage = (MoveMessage) getMessage;

                if (!moveToStorage.getAgv().isOccupied()) {

                    int craneLocation = 0;
                    /*Calendar localCal = GregorianCalendar.getInstance();
                     localCal.setTime(moveToStorage.getAgv().getContainer().getDepartureDate());

                     if (localCal.get(Calendar.HOUR_OF_DAY) <= 6 && localCal.get(Calendar.HOUR_OF_DAY) >= 0) {
                     craneLocation = 0;
                     } else if (localCal.get(Calendar.HOUR_OF_DAY) <= 12 && localCal.get(Calendar.HOUR_OF_DAY) >= 7) {
                     craneLocation = 1;
                     } else if (localCal.get(Calendar.HOUR_OF_DAY) <= 18 && localCal.get(Calendar.HOUR_OF_DAY) >= 13) {
                     craneLocation = 2;
                     } else if (localCal.get(Calendar.HOUR_OF_DAY) <= 24 && localCal.get(Calendar.HOUR_OF_DAY) >= 19) {
                     craneLocation = 3;
                     */

                    crane = findstoragecrane(moveToStorage.getAgv().getContainer().getDepartureTransportType());

                    dijkstra = getDijkstraPath(moveToStorage.getAgv(), crane);
                    moveToStorage.getAgv().setOccupied(true);

                    try {
                        MoveMessage moveCreateMessage = new MoveMessage(moveToStorage.getAgv(), dijkstra, crane);

                        messagePool.add(moveCreateMessage);
                        moveToStorage.getAgv().setProcessingMessageId(moveCreateMessage.getId());
                        server.writeMessage(moveCreateMessage.generateXml());
                        break;
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    public String getDijkstraPath(Agv agv, Crane crane) {
        Dijkstra dijkstra = new Dijkstra();
        String beginPoint = "";
        String endPoint = "";
        String dijkie = "";
        float east = -122f;
        float west = 113f;
        if (agv.getContainer() == null) {
            //western ship platform -> goto waypoint P
            if (agv.getX() < 12 && agv.getY() == east) {
                beginPoint = "P";
            } //eastern ship platform -> goto waypoint Q
            else if (agv.getX() < 12 && agv.getY() == west) {
                beginPoint = "Q";
            }//western train platform -> goto waypoint O
            else if (agv.getX() > 110f && agv.getX() < 300 && agv.getY() == west) {
                beginPoint = "O";
            } //eastern train platform -> goto waypoint N
            else if (agv.getX() < 110f && agv.getX() < 300 && agv.getY() == east) {
                beginPoint = "N";
            } //western lorry platform -> goto waypoint L
            else if (agv.getX() > 365f && agv.getX() < 550 && agv.getY() == west) {
                beginPoint = "L";
            } else if (agv.getX() > 365f && agv.getX() < 550 && agv.getY() == east) {
                beginPoint = "M";
            }
        } else if (agv.getContainer().getArrivalTransportType().equals("trein")) {
            beginPoint = "G";
        } else if (agv.getContainer().getArrivalTransportType().equals("vrachtauto")) {
            beginPoint = "B";
        } else if (agv.getContainer().getArrivalTransportType().equals("binnenschip")) {
            beginPoint = "N";
        } else if (agv.getContainer().getArrivalTransportType().equals("zeeschip")) {
            beginPoint = "K";
        }
        //only call move method when there's a valid waypoint in the char[] to avoid exception
        if (crane != null) {
            switch (crane.getType()) {
                case "DockingCraneInlandShip":
                    endPoint = "K";
                    break;
                case "DockingCraneSeaShip":
                    endPoint = "J";
                    break;
                case "TrainCrane":
                    endPoint = "I";
                    break;
                case "TruckCrane":
                    endPoint = "D";
                    break;
                case "StorageCrane":
                    if (agv.getContainer().getDepartureTransportType().equals("vrachtauto")) {
                        if (beginPoint.equals("G")) {
                            endPoint = "L";
                        } else {
                            endPoint = "M";
                        }
                    } else if (agv.getContainer().getDepartureTransportType().equals("trein")) {
                        if (beginPoint.equals("G")) {
                            endPoint = "O";
                        } else {
                            endPoint = "N";
                        }
                    } else if (agv.getContainer().getDepartureTransportType().equals("binnenschip")) {
                        if (beginPoint.equals("G")) {
                            endPoint = "'P";
                        } else {
                            endPoint = "Q";
                        }
                    } else if (agv.getContainer().getDepartureTransportType().equals("zeeschip")) {
                        if (beginPoint.equals("G")) {
                            endPoint = "'P";
                        } else {
                            endPoint = "Q";
                        }
                    }
                    break;
            }
            dijkie = dijkstra.shortestPath(beginPoint, endPoint);
            dijkie = dijkie.replace("[[", "");
            dijkie = dijkie.replace("]]", "");
        }
        return dijkie;
    }

    /**
     * Processes all received OK messages. Removes them from the pool and sets
     * the message processors to -1.
     */
    private void handleOkMessages() {
        List<String> xmlMessages = new ArrayList<>();
        while (true) {
            String xmlMessage = server.getMessage();
            if (xmlMessage == null) {
                break;
            }
            xmlMessages.add(xmlMessage);
        }

        for (String message : xmlMessages) {
            try {
                handleOkMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleOkMessage(String xmlMessage) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource source = new InputSource();
        source.setCharacterStream(new StringReader(xmlMessage));

        Document doc = db.parse(source);

        NodeList nodes = doc.getElementsByTagName("id");
        if (nodes.getLength() != 1) {
            throw new Exception(xmlMessage + " is not a valid message");
        }

        int id = Integer.parseInt(nodes.item(0).getTextContent());

        Message message = null;
        boolean nobreak = true;

        for (Message message_ : messagePool) {
            if (message_.getId() == id) {
                message = message_;
                nobreak = false;
                break;
            }
        }
        if (nobreak) {
            throw new Exception(id + " doesn't exist");
        }

        switch (message.getMessageType()) {
            case Message.CREATE:
                handleOkCreateMessage((CreateMessage) message);
                break;
            case Message.ARRIVE:
                arriveMessagesList.add(message);
                ArriveMessage arriveMessage = (ArriveMessage) message;
                if (!arriveMessage.getTransporter().getType().equals("vrachtauto")) {
                    for (int i = 0; i < arriveMessage.getTransporter().getContainers().size(); i++) {
                        Thread.sleep(500);
                        moveAgvArrive(message);
                    }
                } else {
                    moveAgvArrive(message);
                }


                for (Message removeMove : moveMessagesList) {
                    MoveMessage moveMessage = (MoveMessage) removeMove;
                    if (moveMessage != null || moveMessage.getAgv().getProcessingMessageId() == 1
                            && moveMessage.getAgv().getContainer() != null) {
                        handleOkMoveMessage(moveMessage);
                    }
                }
                break;
            case Message.SPEED:
                handleOkSpeedMessage((SpeedMessage) message);
                break;
            case Message.CRANE:
                handleOkCraneMessage((CraneMessage) message);
                for (Message removeArrive : arriveMessagesList) {
                    ArriveMessage arrivedMessage = (ArriveMessage) removeArrive;
                    if (arrivedMessage.getTransporter().getProcessingMessageId() == 1) {
                        handleOkArriveMessage(arrivedMessage);
                    }
                }
                break;
            case Message.MOVE:
                MoveMessage moveMsg = (MoveMessage) message;
                moveMsg.getAgv().setOccupied(false);
                moveMessagesList.add(message);
                moveCranes(moveMsg);
                break;

            case Message.DEPART:
                handleOkDepartMessage((DepartMessage) message);
                break;
        }
        messagePool.remove(message);


    }

    private void handleOkCreateMessage(CreateMessage message) {
        message.getTransporter().setProcessingMessageId(-1);
    }

    private void handleOkArriveMessage(Message message) {
        ArriveMessage currentMsg = (ArriveMessage) message;
        Agv agv = findAgv(message);
        agv.setOccupied(false);
        arriveMessagesList.add(message);
    }

    private void handleOkSpeedMessage(SpeedMessage message) {
        this.speed = message.getSpeed();
    }

    private void handleOkCraneMessage(CraneMessage message) {
        try {
            Thread.sleep(1000);
            message.getCrane().setProcessingMessageId(-1);
            message.getCrane().setOccupied(false);

            Crane crane = findCrane(message.getCrane().getType(), message.getCrane().getId());
            crane.setOccupied(false);
            crane.setProcessingMessageId(-1);
            Container con = findContainerByNumber(message.getTransporter().getContainers(), message.getContainer().getNumber());
            Container junk = message.getTransporter().popContainerFromDeque(con);


            CraneMessage craneMessage = (CraneMessage) message;
            craneMessage.getCrane().setOccupied(false);
            craneMessage.getAgv().setOccupied(false);
            moveAgvCrane(message);
            departTransporter(message);
        } catch (Exception e) {
        }

    }

    private void handleOkMoveMessage(MoveMessage message) {
    }

    private void handleOkDepartMessage(DepartMessage message) {
        message.getTransporter().setProcessingMessageId(-1);
    }

    private void updateDatabase(Crane crane) {
        switch (crane.getType()) {
            case "TrainCrane":
                database.updateTrainCrane(crane);
                break;
            case "TruckCrane":
                database.updateTruckCrane(crane);
                break;
            case "DockingCraneInlandShip":
                database.updateDockingCraneInland(crane);
                break;
            case "DockingCraneSeaShip":
                database.updateDockingCraneSea(crane);
                break;
        }

    }

    @Override
    public void run() {
        start();
    }
}
package org.nhl.containing_backend.models;

import java.util.ArrayList;
import org.nhl.containing_backend.cranes.DockingCrane;
import org.nhl.containing_backend.cranes.StorageCrane;
import org.nhl.containing_backend.vehicles.Agv;
import org.nhl.containing_backend.vehicles.Transporter;

import java.util.HashMap;
import java.util.List;
import org.nhl.containing_backend.cranes.TrainCrane;
import org.nhl.containing_backend.cranes.TruckCrane;

/**
 * Representation of the state of the program.
 */
public class Model {

    private List<Container> containerPool;
    private List<Agv> agvs;
    private List<Transporter> transporters;
    private HashMap<String, Transporter[]> depots;
    private Storage storage;
    private List<StorageCrane> storageCranes;
    private List<DockingCrane> dockingCranes;
    private List<TrainCrane> trainCranes;
    private List<TruckCrane> truckCranes;
    private final int MAXAGV = 143;
    private List<Float> agvParkingX;
    private List<Float> agvParkingY;

    public Model() {
        containerPool = new ArrayList<Container>();
        agvs = new ArrayList<Agv>();
        transporters = new ArrayList<Transporter>();
        depots = new HashMap<String, Transporter[]>();
        depots.put("vrachtauto", new Transporter[20]);
        depots.put("trein", new Transporter[1]);
        depots.put("binnenschip", new Transporter[2]);
        depots.put("zeeschip", new Transporter[1]);
        storageCranes = new ArrayList<StorageCrane>();
        dockingCranes = new ArrayList<DockingCrane>();
        trainCranes = new ArrayList<TrainCrane>();
        truckCranes = new ArrayList<TruckCrane>();
        agvParkingX = new ArrayList<>();
        agvParkingY = new ArrayList<>();
        initStartModel();
    }

    /**
     * Figure out which sets of depots aren't currently occupied by a
     * transporter.
     *
     * @return A dictionary with the type as key, and a list of available depots
     * (indices) for that type as value.
     */
    public HashMap<String, List<Integer>> availableDepots() {
        HashMap<String, List<Integer>> result = new HashMap<String, List<Integer>>();
        for (String key : depots.keySet()) {
            result.put(key, availableDepotsForType(key));
        }
        return result;
    }

    private List<Integer> availableDepotsForType(String type) {
        List<Integer> result = new ArrayList<Integer>();
        for (int i = 0; i < depots.get(type).length; i++) {
            if (depots.get(type)[i] == null) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Create starting model
     */
    private void initStartModel() {
        initDockingCrane();
        initStorageCrane();
        initTrainCrane();
        initTruckCrane();
        agvToParking();
    }

    /**
     * Spawn agv on given parkingspace and add it to agv list
     *
     * @param id used to place agv on the given parkingspace
     */
    private void agvToParking() {

        for (int i = 0; i < MAXAGV; i++) {
            Agv agv = new Agv();
            float agvX = i;
            float agvY = i;
            agv.setX(agvX);
            agv.setY(agvY);
            agvs.add(agv);
        }
    }

    private void initDockingCrane() {
        for (int i = 0; i < 18; i++) {
            DockingCrane dockingCrane = new DockingCrane("DockingCrane");
            dockingCrane.setId(i);
            dockingCranes.add(dockingCrane);

        }
    }

    private void initStorageCrane() {
        for (int i = 0; i < 12; i++) {
            StorageCrane storageCrane = new StorageCrane("StorageCrane");
            storageCrane.setId(i);
            storageCranes.add(storageCrane);
        }
    }

    private void initTrainCrane() {
        for (int i = 0; i < 4; i++) {
            TrainCrane trainCrane = new TrainCrane("TrainCrane");
            trainCrane.setId(i);
            trainCranes.add(trainCrane);
        }
    }

    private void initTruckCrane() {
        for (int i = 0; i < 20; i++) {
            TruckCrane truckCrane = new TruckCrane("TruckCrane");
            truckCrane.setId(i);
            truckCranes.add(truckCrane);
        }
    }

    public List<Container> getContainerPool() {
        return containerPool;
    }

    public List<Agv> getAgvs() {
        return agvs;
    }

    public List<DockingCrane> getDockingCrane() {
        return dockingCranes;
    }

    public List<StorageCrane> getStorageCrane() {
        return storageCranes;
    }

    public List<TrainCrane> getTrainCranes() {
        return trainCranes;
    }

    public List<TruckCrane> getTruckCranes() {
        return truckCranes;
    }

    public List<Transporter> getTransporters() {
        return transporters;
    }

    public HashMap<String, Transporter[]> getDepots() {
        return depots;
    }

    public Storage getStorage() {
        return storage;
    }
}

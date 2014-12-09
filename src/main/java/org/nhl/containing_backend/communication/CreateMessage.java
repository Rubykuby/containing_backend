package org.nhl.containing_backend.communication;

import org.nhl.containing_backend.models.Container;
import org.nhl.containing_backend.vehicles.Transporter;

/**
 *
 */
public class CreateMessage extends Message {

    private Transporter transporter;

    public CreateMessage(Transporter transporter) {
        super();
        this.transporter = transporter;
    }

    @Override
    public String generateXml() {
        String message = "";
        message += "<id>" + getId() + "</id>";
        message += "<Create>";
        message += "<Transporter>";

        message += "<identifier>" + transporter.getId() + "</identifier>";
        message += "<type>" + transporter.getContainers().get(0).getArrivalTransportType() + "</type>";
        for (Container container : transporter.getContainers()) {
            message += container.toXml();
        }

        message += "</Transporter>";
        message += "</Create>";

        return message;
    }

    @Override
    public ProcessesMessage getProcessor() {
        return transporter;
    }
}

package org.nhl.containing_backend.communication;

import java.io.PrintWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Sender thread.
 */
class SendRunnable implements Runnable {

    PrintWriter out;
    ConcurrentLinkedQueue<String> queue;
    private boolean running;

    public SendRunnable(PrintWriter out) {
        this.out = out;
        this.queue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void run() {
        String outputLine;
        running = true;

        while (running) {
            outputLine = queue.poll();
            if (outputLine == null) {
                try {
                    Thread.sleep(500);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else {
                // Send outputLine to client
                out.println(outputLine);
                System.out.println("Sent " + outputLine);
            }
        }
    }

    public void stop() {
        running = false;
    }

    public void writeMessage(String message) {
        queue.add(message);
    }
}

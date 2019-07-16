package paxosjade;


import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.MessageTemplate;
import java.util.Random;



public class ClientAgent extends BasicAgent {
    
    private Double value;
    
    // Agent init
    @Override
    protected void setup() {
        // Print a welcome message
        System.out.println("Paxos agent [" + getAID().getLocalName() +  
                           "] is ready...");
        
        // Register Client Agent in the Yellow Pages
        registerDF(getAID(), "Client");

        addBehaviour(new ReceiveConfirmation());
        
        addBehaviour(new TickerBehaviour(this, 10000) {
            // Make Requests to Proposer agents every 10 secs.
            protected void onTick() {
                // Query the DF for the Agents who offer Proposer Services
                AID [] proposerAgents = searchDF("Proposer");
                // Check only one Proposer agent to process the request
                if ( proposerAgents.length == 1 ) {
                    // Create the Request message
                    ACLMessage message = new ACLMessage(ACLMessage.REQUEST);
                    message.setSender(getAID());
                    // Print server name
                    message.addReceiver(new AID(proposerAgents[0].getLocalName(), AID.ISLOCALNAME));
                    Random randomGenerator = new Random();
                    value = randomGenerator.nextDouble();
                    message.setContent(String.valueOf(value));
                    myAgent.send(message);
                    System.out.println("Agent [" + getAID().getLocalName() +
                                       "] sent REQUEST with value (" + 
                                       String.valueOf(value) + ")");
                } else {
                    System.out.println("Error: There are (" + String.valueOf(proposerAgents.length) + ") server agents online.");
                    System.out.println("Waiting to retry...");    

                }
            }
        } ); 
        
    }
    


    protected class ReceiveConfirmation extends CyclicBehaviour {
        
        @Override
        public void action() {
            
            // Get The Request Confirmation
            MessageTemplate mt =  MessageTemplate.MatchConversationId("RESPONSE");

            ACLMessage msg = myAgent.receive(mt);
            
            if (msg == null) { block(); return; }

            switch (msg.getPerformative()) {     
                case (ACLMessage.CONFIRM):
                    System.out.println("Agent [" + getAID().getLocalName() + 
                                       "] received REQUEST CONFIRMATION from: [" + 
                                       msg.getSender().getLocalName() + 
                                       "] with value: " + msg.getContent());

                    break;
                case (ACLMessage.FAILURE):
                    System.out.println("Agent [" + getAID().getLocalName() + 
                                       "] received REQUEST FAILURE from: [" +
                                       msg.getSender().getLocalName() +
                                       "] with value: " + msg.getContent());
                    break;
            }
        }
    }
    

    
    @Override
    protected void takeDown() {
        // Put agent clean-up operations here
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Printout a dismissal message
        System.out.println("Agent " + getAID().getName() + " terminating.");
    } // End of takeDown()
        
    
}
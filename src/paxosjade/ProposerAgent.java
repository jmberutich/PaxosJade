package paxosjade;


import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;

import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import java.io.IOException;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;




public class ProposerAgent extends BasicAgent {
    
    protected final int quorumSize=3;
    protected String proposerUID;

    protected ProposalID proposalID = null;
    protected Double proposedValue = null;
    protected ProposalID lastAcceptedID     = null;
    protected HashSet<String> promisesReceived = new HashSet<String>();
    
    private boolean leader = false;
    private boolean active = true;

    
    protected void setup() {
        // Print a welcome message
        System.out.println("Paxos agent [" + getAID().getLocalName() +  
                           "] is ready...");
   
             
        // Register Client Agent in the Yellow Pages
        registerDF(getAID(), "Proposer");
        
        // Check persistance ... otherwise 0
        proposalID = new ProposalID(0, getLocalName());
 
        addBehaviour(new ProposerBehaviour());
        
    } // End of inner method setup
        
    
    private class ProposerBehaviour extends CyclicBehaviour {
       
        private int step = 0;
        private int acceptedCount = 0;
        
                
        @Override
        public void action() {
           
            switch(step) {
                case 0:
                    // Receive the requests from clients 
                    MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
                    ACLMessage msg = myAgent.receive(mt);
            
                    if (msg == null) { block(); return; }
            
                    try {
                        // REQUEST Message received. Process it
                        System.out.println("Agent [" + getAID().getLocalName() + 
                                           "] received request from: [" + 
                                           msg.getSender().getLocalName() + 
                                           "] with value (" + 
                                           msg.getContent() + ")" );
                        
                        proposalID.setFromAID(msg.getSender());
                        
                        if ( proposedValue == null )
                            proposedValue = Double.parseDouble(msg.getContent());
                        
                        if (leader && active)
                            step = 1;
                        
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                case 1:
                    // Send Prepare Requests to Acceptors
                    // Phase 1A PREPARE
                    // Proposal with identifer N is sent to the Acceptors
                    // attempting to assemble an absolute majority.
                    try {
                        leader = false;
                        promisesReceived.clear();
                        proposalID.incrementNumber();

                        if (active) {
                            // Query the DF for the Agents who offer Acceptor Services
                            AID [] acceptorAgents = searchDF("Acceptor");

                            // Create the PREPARE message
                            // We use the CFP (Call for proposals) as the msg template

                            ACLMessage message = new ACLMessage(ACLMessage.CFP);
                            message.setSender(getAID());
                            for (int i=0; i<acceptorAgents.length; i++) {
                                message.addReceiver(new AID(acceptorAgents[i].getLocalName(), AID.ISLOCALNAME));
                            }
                            message.setConversationId("PREPARE");
                            message.setReplyWith("PREPARE_" + System.currentTimeMillis());
                            message.setContentObject(proposalID);

                            myAgent.send(message);

                            System.out.println("Agent [" + getAID().getLocalName() + 
                                               "] sending PREPARE message to all acceptor agents..."); 

                            step = 2;
                        }
                        break;
                    } catch ( IOException ex) {
                        ex.printStackTrace();
                    }                   
                case 2:
                    // Receive PREPARE PROMISES from Acceptors
                    MessageTemplate promiseTemplate = MessageTemplate.MatchConversationId("PROMISE");
                    
                    ACLMessage promiseMsg = myAgent.receive(promiseTemplate);

                    if (promiseMsg == null) { block(); return; }
                    
                    // Extract the object from message
                    SendPromiseMsgObj sendPromiseMsgObj;
                    try {
                        sendPromiseMsgObj = (SendPromiseMsgObj) promiseMsg.getContentObject();
                    } catch (UnreadableException ex) {
                        ex.printStackTrace();
                    }
                    
                    // Extract the objects from the sendPromiseMsgObj
                    ProposalID promisedID = sendPromiseMsgObj.getProposalID();
                    ProposalID prevAcceptedID = sendPromiseMsgObj.getPreviousID();
                    Double prevAcceptedValue = sendPromiseMsgObj.getAcceptedValue();

                    switch ( promiseMsg.getPerformative() ) {     
                        case (ACLMessage.PROPOSE):
                            if ( promisedID.isGreaterThan(proposalID))
                                proposalID.setNumber(promisedID.getNumber());

                            System.out.println("Agent [" + getAID().getLocalName() +
                                               "] received promise from: [" + 
                                               promiseMsg.getSender().getLocalName() + 
                                               "] with previous accepted value (" + 
                                               String.valueOf(prevAcceptedValue) + ")" );

                            if ( leader || !promisedID.equals(proposalID) || promisesReceived.contains(promiseMsg.getSender().getLocalName()) )
                                return;

                            promisesReceived.add( promiseMsg.getSender().getLocalName() );

                            if (lastAcceptedID == null || prevAcceptedID.isGreaterThan(lastAcceptedID)) { 
                                lastAcceptedID = prevAcceptedID;                  
                                if (prevAcceptedValue != null) {
                                    proposedValue = prevAcceptedValue;

                                }
                            } 

                            if (promisesReceived.size() == quorumSize) {
                                leader = true;
                                // need to call onLeadershipAcquired()
                                if (proposedValue != null) {
                                    System.out.println("Agent [" + getAID().getLocalName() + 
                                               "] received all responses from QUORUM." + 
                                                " Sending ACCEPT REQUESTS to acceptors...");

                                    step = 3;
                                }        
                            }
                            break;
                        case (ACLMessage.FAILURE):
                            if (proposalID.isGreaterThan(promisedID))
                                proposalID.setNumber(promisedID.getNumber());
                            break;
                    }          
                    break;
                case 3:
                    // Send Accept Requests to Acceptors
                    try {                    
                        // Query the DF for the Agents who offer Acceptor Services
                        AID [] acceptorAgents = searchDF("Acceptor");
                        // Create the ACCEPT message                
                        ACLMessage message = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                        message.setSender(getAID());
                        for ( int i=0; i<acceptorAgents.length; i++) {
                            message.addReceiver(new AID(acceptorAgents[i].getLocalName(), AID.ISLOCALNAME));
                        }
                        message.setConversationId("ACCEPT");
                        message.setReplyWith("ACCEPT_" + System.currentTimeMillis());

                        SendAcceptMsgObj sendAcceptMsgObj = new SendAcceptMsgObj(proposalID, proposedValue);
                        message.setContentObject(sendAcceptMsgObj); 
                        myAgent.send(message);  
                        step = 4;
                        break;
                    } catch ( IOException ex) {
                        ex.printStackTrace();
                    } 
                    
                case 4:
                    // Receive Accepted 
                    // Could be merged with learner behaviour 
                    
                    // Add NACK  - funcinality .. match conversation - performative disagree
                    
                    MessageTemplate acceptedTemplate = MessageTemplate.and(MessageTemplate.MatchConversationId("ACCEPTED"),
                                                                           MessageTemplate.MatchPerformative(ACLMessage.AGREE));
            
                    ACLMessage acceptedMsg = myAgent.receive(acceptedTemplate);

                    if (acceptedMsg == null) { block(); return; }

                    try {
                        SendAcceptMsgObj sendAcceptMsgObj = (SendAcceptMsgObj) acceptedMsg.getContentObject();
                        ProposalID acceptedProposalID = sendAcceptMsgObj.getProposalID();
                        Double acceptedValue =  sendAcceptMsgObj.getAcceptedValue();

                        System.out.println("Agent [" + getAID().getLocalName() + 
                                           "] received ACCEPTED proposalID (" + 
                                            acceptedProposalID.getUID() +  String.valueOf(acceptedProposalID.getNumber() +
                                            ") with value: (" + String.valueOf(acceptedValue)) + ") " +
                                            " from: [" + acceptedMsg.getSender().getLocalName() + "]" );
                        
                        acceptedCount += 1;
                        
                        if (acceptedCount >= quorumSize) {
                            lastAcceptedID = acceptedProposalID;
                            // reset variables
                            proposedValue = null;
                            acceptedCount = 0;
                            // goto begining of behaviour
                            step = 0;
                            break;
                        }
                        
                    } catch (UnreadableException ex) {
                            ex.printStackTrace();  
                    }
                    
                    
            }            
	
        
        }
        
    }    


    
    // Put agent clean-up operations here
    @Override
    protected void takeDown() {
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

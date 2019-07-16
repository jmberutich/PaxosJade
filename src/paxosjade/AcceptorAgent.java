package paxosjade;


import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;






public class AcceptorAgent extends BasicAgent {
     
    private ProposalID receivedID = null;
    private ProposalID promisedID = null;
    private ProposalID acceptedID = null;
    private Double acceptedValue;

    protected String  pendingAccepted = null;
    protected String  pendingPromise  = null;
    protected boolean active          = true;
    
   
    
    protected void setup() {
        // Print a welcome message
        System.out.println("Paxos agent [" + getAID().getLocalName() +  
                           "] is ready...");
   
        // Register Client Agent in the Yellow Pages
        registerDF(getAID(), "Acceptor");
        
        addBehaviour(new AcceptorSeqBehaviour());
    }
     
    
    private class AcceptorSeqBehaviour extends CyclicBehaviour {
        private int step = 0;
        ACLMessage msg = null;
        MessageTemplate mt = null;
       
        
        @Override
        public void action() {     
            switch (step) {
                case 0:
                    // Receive PREPARE
                    // Get CFP messages
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("PREPARE"),
                                             MessageTemplate.MatchPerformative(ACLMessage.CFP));;

                    msg = myAgent.receive(mt);

                    if (msg == null) { block(); return; }

                    try {
                        receivedID = (ProposalID) msg.getContentObject();
                        System.out.println("Agent [" + getAID().getLocalName() + 
                                       "] received PREPARE message from: [" + 
                                       msg.getSender().getLocalName() + 
                                       "] with ID (" + receivedID.getUID() + 
                                       String.valueOf(receivedID.getNumber()) + 
                                       ")" );

                        if (promisedID != null && receivedID.equals(promisedID)) { // duplicate message
                            if (active) 
                                step=1;
                        } else if (promisedID == null || receivedID.isGreaterThan(promisedID)) {
                            if (pendingPromise == null) {
                                promisedID = receivedID;
                                if (active)
                                    pendingPromise = msg.getSender().getLocalName();
                            }    
                        } else { 
                            if (active) {
                                //sendPrepareNACK 
                                // proposerUID - proposalID - promisedID
                                ACLMessage message = new ACLMessage(ACLMessage.FAILURE);
                                AID[] proposerAgents = searchDF("Proposer");

                                for (AID proposerAgent : proposerAgents) {
                                    message.addReceiver(new AID(proposerAgent.getLocalName(), AID.ISLOCALNAME));
                                }

                                message.setConversationId("PROMISE");
                                message.setInReplyTo(msg.getReplyWith());
                                message.setReplyWith("PROMISE_" + System.currentTimeMillis());
                                
                                SendPromiseMsgObj sendPromiseMsgObj = new SendPromiseMsgObj(promisedID, acceptedID, acceptedValue);

                                try {
                                    message.setContentObject(sendPromiseMsgObj);
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }

                                myAgent.send(message);
                            } // End sendPrepareNACK
                        }
                        break;
                    } catch (UnreadableException ex) {
                        ex.printStackTrace();
                    }
                case 1:
                    // Send PROMISE
                    try {
                        // Send the message as PROPOSE
                        ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);

                        AID[] proposerAgents = searchDF("Proposer");

                        for (AID proposerAgent : proposerAgents) {
                            message.addReceiver(new AID(proposerAgent.getLocalName(), AID.ISLOCALNAME));
                        }

                        message.setConversationId("PROMISE");
                        message.setInReplyTo(msg.getReplyWith());
                        message.setReplyWith("PROMISE_" + System.currentTimeMillis());

                        SendPromiseMsgObj sendPromiseMsgObj = new SendPromiseMsgObj(promisedID, acceptedID, acceptedValue);

                        message.setContentObject(sendPromiseMsgObj);

                        myAgent.send(message);

                        System.out.println("Agent [" + getAID().getLocalName() + 
                                           "] sending PROMISE to all Proposer Agents " + 
                                           " with recevied ID (" + receivedID.getUID() + 
                                           String.valueOf(receivedID.getNumber()) + 
                                           ") and previous accepted value (" + 
                                            String.valueOf(acceptedValue) + ")" );
                        
                        step = 2;
                        break;

                    } // End of private method sendPromise
                    catch (IOException ex) {
                        ex.printStackTrace();
                    }       
                case 2:
                    // Receive ACCEPT REQUEST
                    // Phase 2.B ACCEPTED
                    // Should an Acceptor receive an Accept Request for a proposal 
                    // with number N, it will accept that proposal unless it had 
                    // responded to a different Prepare message numbered greater than N.
                    // If it had, for the purpose of optimization, it may send a NACK 
                    // message to the Proposer but it is not required to.

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("ACCEPT"),
                                             MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));;
                    msg = myAgent.receive(mt);

                    if (msg == null) { block(); return; }

                    try {
                        SendAcceptMsgObj sendAcceptMsgObj = (SendAcceptMsgObj) msg.getContentObject();
                        ProposalID proposalID = sendAcceptMsgObj.getProposalID();
                        Double value = sendAcceptMsgObj.getAcceptedValue();

                        System.out.println("Agent [" + getAID().getLocalName() + 
                                "] received ACCEPT request with value: (" + 
                                String.valueOf(value) + ")");

                        if (acceptedID != null && 
                            proposalID.equals(acceptedID) && 
                            acceptedValue.equals(value) ) {
                                if (active)
                                    step = 3; // send ACCEPTED
                                    
                        }                     
                        else if (promisedID == null || proposalID.isGreaterThan(promisedID) 
                                                    || proposalID.equals(promisedID)) {
                            if (pendingAccepted == null) {
                                promisedID    = proposalID;
                                acceptedID    = proposalID;
                                acceptedValue = value;

                                if (active)
                                    pendingAccepted = msg.getSender().getLocalName();
                                
                            }
                        } else {
                            if (active)
                                sendAcceptNACK();
                        }
                            
                        break;
                    } catch (UnreadableException ex) {
                                ex.printStackTrace();
                    }
                case 3:
                    // Send ACCEPTED
                    // Query the DF for Proposers
                    // Create the ACCEPT message
                    // We use the ACLMessage INFORM for this
                    ACLMessage message = new ACLMessage(ACLMessage.AGREE);
                    message.setSender(getAID());
                    message.setReplyWith("ACCEPTED_" + System.currentTimeMillis());
                    message.setInReplyTo(msg.getReplyWith());
                    message.setConversationId("ACCEPTED");
                    SendAcceptMsgObj sendAcceptMsgObj = new SendAcceptMsgObj(promisedID, acceptedValue);
                    try {
                        message.setContentObject(sendAcceptMsgObj);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    // Send message to Proposers
                    AID[] proposerAgents = searchDF("Proposer");
                    for (AID proposerAgent : proposerAgents) {
                        message.addReceiver(new AID(proposerAgent.getLocalName(), AID.ISLOCALNAME));
                    }
                    // Send message to Learners
                    AID[] learnerAgents = searchDF("Learner");
                    for (AID learnerAgent : learnerAgents) {
                        message.addReceiver(new AID(learnerAgent.getLocalName(), AID.ISLOCALNAME));
                    }

                    myAgent.send(message);
                    step = 0;
                    break;
            } 
                   
        }

        private void sendPrepareNACK() {
            // TO DO - send PREPARE NACK TO proposer
            
        }
        
        private void sendAcceptNACK() {
            // TO DO -send ACCEPT NACK to proposer
        }
        
        public boolean persistenceRequired() {
		return pendingAccepted != null || pendingPromise != null;
	}
        
        public void persist() {
            try {
                FileOutputStream fileOut = new FileOutputStream("/tmp/acceptor_promisedID.ser");
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(promisedID);
                out.close();
                fileOut.close();
                System.out.printf("Agent [" + getAID().getLocalName() + "] serialized promisedID saved in /tmp/acceptor_promisedID.ser");
                
                fileOut = new FileOutputStream("/tmp/acceptor_acceptedID.ser");
                out = new ObjectOutputStream(fileOut);
                out.writeObject(promisedID);
                out.close();
                fileOut.close();
                System.out.printf("Agent [" + getAID().getLocalName() + "] serialized acceptedID saved in /tmp/acceptor_acceptedID.ser");
                
                fileOut = new FileOutputStream("/tmp/acceptorValue.ser");
                out = new ObjectOutputStream(fileOut);
                out.writeObject(acceptedValue);
                out.close();
                fileOut.close();
                System.out.printf("Agent [" + getAID().getLocalName() + "] serialized acceptedValue saved in /tmp/acceptor_acceptedValue.ser");
            } catch(IOException i) {
                i.printStackTrace();
            }
        }
        
        public void recover() {
            // Recover from persistence
            try {
                FileInputStream promisedIDfile = new FileInputStream("/tmp/acceptor_promisedID.ser");
                ObjectInputStream inputPromise = new ObjectInputStream(promisedIDfile);
                try {
                    promisedID = (ProposalID) inputPromise.readObject();
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
                inputPromise.close();
                promisedIDfile.close();

                FileInputStream acceptedIDfile = new FileInputStream("/tmp/acceptor_acceptedID.ser");
                ObjectInputStream inputAccepted = new ObjectInputStream(acceptedIDfile);
                try {
                    acceptedID = (ProposalID) inputAccepted.readObject();
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
                inputAccepted.close();
                acceptedIDfile.close();

                FileInputStream acceptedValuefile = new FileInputStream("/tmp/acceptor_acceptedValue.ser");
                ObjectInputStream inputAcceptedValue = new ObjectInputStream(acceptedValuefile);
                try {
                    acceptedValue = (Double) inputAcceptedValue.readObject();
                } catch (ClassNotFoundException ex) {
                    ex.printStackTrace();
                }
                inputAccepted.close();
                acceptedIDfile.close();

            } catch(IOException i) {
                    i.printStackTrace();
            }
        }        
  
        public void persisted() {
		/** if (active) {
			if (pendingPromise != null)
				sendPromise(pendingPromise, promisedID, acceptedID, acceptedValue);
			if (pendingAccepted != null)
				messenger.sendAccepted(acceptedID, acceptedValue);
		} 
                **/
		pendingPromise  = null;
		pendingAccepted = null;
                    
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
} // End of class AcceptorAgent

package paxosjade;


import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import java.util.HashMap;




public class LearnerAgent extends BasicAgent {
    
    protected String              proposerUID;
    protected final int           quorumSize=3;
    private HashMap<ProposalID, Proposal> proposals       = new HashMap<ProposalID, Proposal>();
    private HashMap<String,  ProposalID>  acceptors       = new HashMap<String, ProposalID>();
    private Double                        finalValue      = null;
    private ProposalID                    finalProposalID = null;
    
    
    class Proposal {
	int    acceptCount;
	int    retentionCount;
	Object value;
		
	Proposal(int acceptCount, int retentionCount, Double value) {
		this.acceptCount    = acceptCount;
		this.retentionCount = retentionCount;
		this.value          = value;
	}
    }
    
    
    protected void setup() {
        // Print a welcome message
        System.out.println("Paxos agent [" + getAID().getLocalName() +  
                           "] is ready...");
               
        // Register Client Agent in the Yellow Pages
        registerDF(getAID(), "Learner");
  
        addBehaviour(new ReceiveAccepted());
    }
    
    
    private class ReceiveAccepted extends CyclicBehaviour {
        
        // Learners - 
        // they ensure that the request is carried out
        // in the distributed system and a response is sent to
        // the Client by each informed Learner, as soon as they
        // receive the agreement notice from the Acceptors. Multiple 
        // Learners may be added to increase availability.
        
        private boolean isComplete() {
            return finalValue != null;
	}
        
        
        @Override
        public void action() {
        	    
            if (isComplete()) return;
            
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchConversationId("ACCEPTED"),
                        MessageTemplate.MatchPerformative(ACLMessage.AGREE));
            
            ACLMessage msg = myAgent.receive(mt);
            
            if (msg == null) { block(); return; }
            
            try {
                SendAcceptMsgObj sendAcceptMsgObj = (SendAcceptMsgObj) msg.getContentObject();
                ProposalID proposalID = sendAcceptMsgObj.getProposalID();
                Double acceptedValue =  sendAcceptMsgObj.getAcceptedValue();
                ProposalID oldPID = acceptors.get(msg.getSender().getLocalName());
            	
                if (oldPID != null && !proposalID.isGreaterThan(oldPID)) return;
		
                acceptors.put(msg.getSender().getLocalName(), proposalID);

                if (oldPID != null) {
                    Proposal oldProposal = proposals.get(oldPID);
                    oldProposal.retentionCount -= 1;
                    if (oldProposal.retentionCount == 0) proposals.remove(oldPID);
                }
        
                if (!proposals.containsKey(proposalID))
                    proposals.put(proposalID, new Proposal(0, 0, acceptedValue));

                Proposal thisProposal = proposals.get(proposalID);	

                thisProposal.acceptCount    += 1;
                thisProposal.retentionCount += 1;

                if (thisProposal.acceptCount == quorumSize) {
                    finalProposalID = proposalID;
                    finalValue      = acceptedValue;
                    sendResolution();
                    // Clear finalValue
                    finalValue = null;
                    proposals.clear();
                    acceptors.clear();
                }
                
            } catch (UnreadableException ex) {
                    ex.printStackTrace();  
            }
	} // End of inner method receiveAccepted()
        
        
        private void sendResolution() {
            // Send client the confirm/failure
            ACLMessage message = new ACLMessage(ACLMessage.CONFIRM);
            message.addReceiver(finalProposalID.getFromAID());
            message.setConversationId("RESPONSE");
            message.setContent(String.valueOf(finalValue));
            myAgent.send(message);
            
            
            
        } // End of private class sendResolution()
        
    } // End of ReceiveAccepted behaviour
    
    
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

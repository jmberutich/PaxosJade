package paxosjade;

import java.io.Serializable;


public class SendAcceptMsgObj implements Serializable{
    private ProposalID proposalID;
    private Double acceptedValue;

    
    SendAcceptMsgObj(ProposalID proposalID, Double acceptedValue) {
        this.proposalID = proposalID;
        this.acceptedValue = acceptedValue;    
    }

    public ProposalID getProposalID() {
        return proposalID;
    }
    
    public void setProposalID(ProposalID p) {
        proposalID = p;
    }
    
    public Double getAcceptedValue() {
        return acceptedValue;
    }
    
    public void setAcceptedValue(Double v) {
        acceptedValue = v;
    }
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package paxosjade;

import java.io.Serializable;

/**
 *
 * @author jm
 */
public class SendPromiseMsgObj implements Serializable{
    private ProposalID proposalID;
    private ProposalID previousID;
    private Double acceptedValue;

    
    SendPromiseMsgObj(ProposalID proposalID, ProposalID previousID, Double acceptedValue) {
        this.proposalID = proposalID;
        this.previousID = previousID;
        this.acceptedValue = acceptedValue;
    }

    public ProposalID getProposalID() {
        return proposalID;
    }
    
    public void setProposalID(ProposalID p) {
        proposalID = p;
    }
    
    public ProposalID getPreviousID() {
        return previousID;
    }

    public void setPreviousID(ProposalID p) {
        previousID = p;
    }
    
    public Double getAcceptedValue() {
        return acceptedValue;
    }
    
    public void setAcceptedValue(Double v) {
        acceptedValue = v;
    }
    
}

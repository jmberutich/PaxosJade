
package paxosjade;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import static jade.tools.sniffer.Agent.i;



abstract class BasicAgent extends Agent {
     
    // Helper methods for the DF

    protected AID getService( String service ) {
        // Get only first agent offering service
        
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType( service );
        dfd.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, dfd);
            if (result.length>0)
                return result[0].getName() ;
        } catch (FIPAException fe) { fe.printStackTrace(); }
        return null;
    } // End of method getService
    

    protected AID [] searchDF( String service ) {
        // Search DF for all agents offering service
        
        DFAgentDescription dfd = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType( service );
        dfd.addServices(sd);
        
        SearchConstraints ALL = new SearchConstraints();
        ALL.setMaxResults(new Long(-1));

        try {
            DFAgentDescription[] result = DFService.search(this, dfd, ALL);
            AID[] agents = new AID[result.length];
            for (i=0; i<result.length; i++) 
                agents[i] = result[i].getName() ;
            return agents;

        } catch (FIPAException fe) { fe.printStackTrace(); }
        
        return null;
    } // End of method searchDF
    
    
    protected void registerDF(AID agentAID, String serviceType ) {
        // Register Agent in the Yellow Pages
        // Agents are only allowed one entry in the DF
        
	DFAgentDescription dfd = new DFAgentDescription();
	dfd.setName(agentAID);
        
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        sd.setName(agentAID.getLocalName());
        
        try {
            DFAgentDescription list[] = DFService.search( this, dfd );
            if ( list.length>0 ) 
            	DFService.deregister(this);
            	
            dfd.addServices(sd);
            DFService.register(this,dfd);
	} catch (FIPAException fe) { fe.printStackTrace(); }
    } // End of method registerDF
    
} // End of class BasicAgent
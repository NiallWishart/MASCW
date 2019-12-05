package supplyChainOntology.actions;

import jade.content.AgentAction;
import jade.content.onto.annotations.Slot;
import supplyChainOntology.concepts.Supplies;

public class SellSupplies implements AgentAction{
	
	private Supplies supplies;
	
	@Slot(mandatory = true)
	public Supplies getSupplies() {
		return supplies;
	}
	
	public void setSupplies(Supplies supplies) {
		this.supplies = supplies;
	}
}


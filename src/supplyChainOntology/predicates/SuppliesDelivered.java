package supplyChainOntology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;
import supplyChainOntology.concepts.Supplies;

public class SuppliesDelivered implements Predicate{

	private Supplies supplies;
	
	@Slot(mandatory = true)
	public Supplies getSupplies() {
		return supplies;
	}
	
	public void setSupplies(Supplies supplies) {
		this.supplies = supplies;
	}
}

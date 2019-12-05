package supplyChainOntology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;

public class PredictedCost implements Predicate{
	private int cost;
	
	@Slot(mandatory = true)
	public int getCost() {
		return cost;
	}
	
	public void setCost(int cost) {
		this.cost = cost;
	}
}

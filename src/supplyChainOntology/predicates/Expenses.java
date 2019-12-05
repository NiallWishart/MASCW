package supplyChainOntology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;

public class Expenses implements Predicate{
	
	private int storageCost;
	private int suppliesCost;
	private int penaltiesCost;
	
	@Slot(mandatory=true)
	public int getStorageCost() {
		return storageCost;
	}
	
	public void setStorageCost(int storageCost) {
		this.storageCost = storageCost;
	}
	
	@Slot(mandatory=true)
	public int getSuppliesCost() {
		return suppliesCost;
	}
	
	public void setSuppliesCost(int suppliesCost) {
		this.suppliesCost = suppliesCost;
	}
	
	@Slot(mandatory=true)
	public int getPenaltiesCost() {
		return penaltiesCost;
	}
	
	public void setPenaltiesCost(int penaltiesCost) {
		this.penaltiesCost = penaltiesCost;
	}

}

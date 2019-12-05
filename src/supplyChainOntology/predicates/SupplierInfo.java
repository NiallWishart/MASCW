package supplyChainOntology.predicates;

import java.util.List;

import jade.content.Predicate;
import jade.content.onto.annotations.AggregateSlot;
import jade.content.onto.annotations.Slot;
import supplyChainOntology.concepts.Component;

public class SupplierInfo implements Predicate{

	private List<Component> components;
	private int deliveryTime;
	
	@AggregateSlot(cardMin = 1)
	public List<Component> getComponents(){
		return components;
	}
	
	public void setComponents(List<Component> components) {
		this.components = components;
	}
	
	@Slot(mandatory = true)
	public int getDeliveryTime() {
		return deliveryTime;
	}
	
	public void setDeliveryTime(int deliveryTime) {
		this.deliveryTime = deliveryTime;
	}
}

package supplyChainOntology.predicates;

import java.util.List;

import jade.content.Predicate;
import jade.content.onto.annotations.AggregateSlot;
import supplyChainOntology.concepts.Order;

public class OrderReady implements Predicate{

	private List<Order> orders;
	
	public List<Order> getOrders(){
		return orders;
	}
	
	public void setOrders(List<Order> orders) {
		this.orders = orders;
	}
}

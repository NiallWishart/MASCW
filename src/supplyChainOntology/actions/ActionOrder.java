package supplyChainOntology.actions;

import jade.content.AgentAction;
import jade.content.onto.annotations.Slot;
import supplyChainOntology.concepts.Order;

public class ActionOrder implements AgentAction{
	
	private Order order;
	
	@Slot(mandatory = true)
	public Order getOrder() {
		return order;
	}
	
	public void setOrder(Order order) {
		this.order = order;
	}

}

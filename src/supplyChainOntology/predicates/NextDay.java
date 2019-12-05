package supplyChainOntology.predicates;

import jade.content.Predicate;
import jade.content.onto.annotations.Slot;

public class NextDay implements Predicate{

	private int dayNumber;
	
	@Slot(mandatory = true)
	public int getDayNumber() {
		return dayNumber;
	}
	
	public void setDayNumber(int dayNumber) {
		this.dayNumber = dayNumber;
	}
}

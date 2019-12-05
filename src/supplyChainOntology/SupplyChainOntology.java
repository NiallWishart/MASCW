package supplyChainOntology;

import jade.content.onto.BeanOntology;
import jade.content.onto.BeanOntologyException;
import jade.content.onto.Ontology;

public class SupplyChainOntology extends BeanOntology{

	private static Ontology theInstance = new SupplyChainOntology("Smartphone_Supply_Chain_Ontology");
	
	public static Ontology getInstance(){
		return theInstance;
	}
	// Singleton pattern
	private SupplyChainOntology(String name) {
		super(name);
		try {
			add("supplyChainOntology.concepts");
			add("supplyChainOntology.predicates");
			add("supplyChainOntology.actions");
		} catch (BeanOntologyException e) {
			e.printStackTrace();
		}
	}
}

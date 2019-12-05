package supplyChain;

import java.util.ArrayList;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import supplyChainOntology.SupplyChainOntology;
import supplyChainOntology.actions.SellSupplies;
import supplyChainOntology.concepts.Battery;
import supplyChainOntology.concepts.Component;
import supplyChainOntology.concepts.Ram;
import supplyChainOntology.concepts.Screen;
import supplyChainOntology.concepts.Storage;
import supplyChainOntology.concepts.Supplies;
import supplyChainOntology.predicates.NextDay;
import supplyChainOntology.predicates.NoMoreSupplies;
import supplyChainOntology.predicates.SupplierDetails;
import supplyChainOntology.predicates.SupplierInfo;
import supplyChainOntology.predicates.SuppliesDelivered;

public class SupplierAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	
	private SupplierInfo supplierInformation;
	private ArrayList<SuppliesToDeliver> pendingDeliveries;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Supplier");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Get the supplier information from the arguments
		Object[] args = this.getArguments();
		supplierInformation = createSupplierInformation((String[])args[0], (int[])args[1], (String[])args[2], (int[])args[3], (String[])args[4], (int[])args[5], (String[])args[6], 
														(int[])args[7], (int)args[8]);
		// Initialise pending deliveries list
		pendingDeliveries = new ArrayList<SuppliesToDeliver>();
		// Wait for other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
		this.addBehaviour(new ProvideDetailsBehaviour());
		this.addBehaviour(new ProcessSuppliesRequestsBehaviour());
	}
	
	// Called when agent is deleted
	protected void takeDown() {
		// Remove agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Find the warehouse agent
	private class FindWarehouseBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription warehouseTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Warehouse");
			warehouseTemplate.addServices(sd);
			try {
				warehouseAID = new AID();
				DFAgentDescription[] warehouseAgents = DFService.search(myAgent, warehouseTemplate);
				warehouseAID = warehouseAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Find the Timer agent
	private class FindDayCoordinatorBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription dayCoordinatorTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("DayCoordinator");
			dayCoordinatorTemplate.addServices(sd);
			try {
				dayCoordinatorAID = new AID();
				DFAgentDescription[] dayCoordinatorAgents = DFService.search(myAgent, dayCoordinatorTemplate);
				dayCoordinatorAID = dayCoordinatorAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Waits for new day or simulation end
	public class DayCoordinatorWaiterBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(dayCoordinatorAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					// Each day supplier can send to warehouse
					if(ce instanceof NextDay) {
						// Add to the supplier the send supplies behaviour
						myAgent.addBehaviour(new SendSuppliesBehaviour());
					} else {
						// If simulation stops, end
						myAgent.doDelete();
					}
					
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
	}
	
	// Provide to the sender with this agent details, this will only happen once
	public class ProvideDetailsBehaviour extends Behaviour{
		private boolean detailsSent = false;
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce =  getContentManager().extractContent(msg);
					if(ce instanceof SupplierDetails) {
						// Give warehouse supplier information
						ACLMessage response = new ACLMessage(ACLMessage.INFORM);
						response.addReceiver(warehouseAID);
						response.setLanguage(codec.getName());
						response.setOntology(ontology.getName());
						try {
							// Convert java objects to string
							getContentManager().fillContent(response, supplierInformation);
							myAgent.send(response);
						} catch (CodecException codece) {
							codece.printStackTrace();
						} catch (OntologyException oe) {
							oe.printStackTrace();
						}
						detailsSent = true;
					}
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
		
		public boolean done() {
			return detailsSent;
		}
	}
	
	// Process requests to sell supplies
	public class ProcessSuppliesRequestsBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert strings to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					if(ce instanceof Action) {
						Concept action = ((Action)ce).getAction();
						if(action instanceof SellSupplies) {
							SellSupplies sellSupplies = (SellSupplies) action;
							// Add supplies to pending deliveries
							SuppliesToDeliver suppliesToDeliver = new SuppliesToDeliver();
							suppliesToDeliver.setDaysLeftToDeliver(supplierInformation.getDeliveryTime());
							suppliesToDeliver.setSupplies(sellSupplies.getSupplies());
							pendingDeliveries.add(suppliesToDeliver);
						}
					}
				} catch (CodecException codece) {
					codece.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}
		}
	}
	
	// Send supplies that are ready at the start of each day
	public class SendSuppliesBehaviour extends OneShotBehaviour{
		public void action() {
			ArrayList<SuppliesToDeliver> deliveriesToRemove = new ArrayList<SuppliesToDeliver>();
			for(SuppliesToDeliver delivery : pendingDeliveries) {
				int days = delivery.getDaysLeftToDeliver();
				days--;
				if(days == 0) {
					// Create predicate
					SuppliesDelivered suppliesDelivered = new SuppliesDelivered();
					suppliesDelivered.setSupplies(delivery.getSupplies());
					// Send supplies to warehouse
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					msg.addReceiver(warehouseAID);
					msg.setLanguage(codec.getName());
					msg.setOntology(ontology.getName());
					try {
						getContentManager().fillContent(msg, suppliesDelivered);
						myAgent.send(msg);
					} catch (CodecException codece) {
						codece.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
					// Set up delivery for removal
					deliveriesToRemove.add(delivery);
				} else {
					// Update days left to deliver
					delivery.setDaysLeftToDeliver(days);
				}
			}
			// Remove deliveries from list of pending deliveries
			pendingDeliveries.removeAll(deliveriesToRemove);
			// Create Predicate
			NoMoreSupplies noMoreSuppliesToday = new NoMoreSupplies();
			// Inform warehouse no more deliveries
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(warehouseAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Transform java objects to strings
				getContentManager().fillContent(msg, noMoreSuppliesToday);
				myAgent.send(msg);
			} catch (CodecException codece) {
				codece.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
		}
	}
	
	// Supplies that need to be sent
	private class SuppliesToDeliver{
		private Supplies supplies;
		private int daysLeftToDeliver;
		
		public void setSupplies(Supplies supplies) {
			this.supplies = supplies;
		}
		
		public Supplies getSupplies() {
			return supplies;
		}
		
		public void setDaysLeftToDeliver(int daysLeftToDeliver) {
			this.daysLeftToDeliver = daysLeftToDeliver;
		}
		
		public int getDaysLeftToDeliver() {
			return daysLeftToDeliver;
		}
		
	}
	
	// Extract the supplier information from the arguments
	private SupplierInfo createSupplierInformation(String[] screenSizes, int[] screenPrices, String[] storageCapacities, int[] storagePrices,
			String[] ramAmounts, int[] ramPrices, String[] batteryCharges, int[] batteryPrices, int deliveryTime) {
		SupplierInfo supplierInformation = new SupplierInfo();
		ArrayList<Component> components = new ArrayList<Component>();
		// Screens
		for(int i = 0; i < screenSizes.length; i++) {
			Screen screen = new Screen();
			screen.setSize(screenSizes[i]);
			screen.setPrice(screenPrices[i]);
			components.add(screen);
		}
		// Storages
		for(int i = 0; i < storageCapacities.length; i++) {
			Storage storage = new Storage();
			storage.setCapacity(storageCapacities[i]);
			storage.setPrice(storagePrices[i]);
			components.add(storage);
		}
		// Rams
		for(int i = 0; i < ramAmounts.length; i++) {
			Ram ram = new Ram();
			ram.setAmount(ramAmounts[i]);
			ram.setPrice(ramPrices[i]);
			components.add(ram);
		}
		// Batteries
		for(int i = 0; i < batteryCharges.length; i++) {
			Battery battery = new Battery();
			battery.setCharge(batteryCharges[i]);
			battery.setPrice(batteryPrices[i]);
			components.add(battery);
		}
		supplierInformation.setComponents(components);
		supplierInformation.setDeliveryTime(deliveryTime);
		return supplierInformation;
	}
}

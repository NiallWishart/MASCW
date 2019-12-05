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
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import supplyChainOntology.SupplyChainOntology;
import supplyChainOntology.actions.CalOrderCost;
import supplyChainOntology.actions.PrepairOrderAssembly;
import supplyChainOntology.actions.SellOrder;
import supplyChainOntology.concepts.Order;
import supplyChainOntology.predicates.EndDay;
import supplyChainOntology.predicates.NextDay;
import supplyChainOntology.predicates.NoMoreOrders;
import supplyChainOntology.predicates.OrderDelivered;
import supplyChainOntology.predicates.OrderReady;
import supplyChainOntology.predicates.Payment;
import supplyChainOntology.predicates.PredictedCost;
import supplyChainOntology.predicates.Expenses;
import supplyChainOntology.predicates.TodaysExpenses;

public class ManufacturerAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID[] customersAID;
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	
	private int dailyProfit;
	private int dailyPurchasesCost;
	private int dailyPenaltiesCost;
	private int dailyWarehouseStorageCost;
	private int dailyPayments;
	
	private int totalProfit;
	private static final int minimumBenefitMargin = 3;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Manufacturer");
		sd.setName(getLocalName() + "-manufacturer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Initialise variables
		totalProfit = 0;
		dailyProfit = 0;
		dailyPurchasesCost = 0;
		dailyPenaltiesCost = 0;
		dailyWarehouseStorageCost = 0;
		dailyPayments = 0;
		// Wait for other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindCustomersBehaviour());
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
	}
	
	// Called when agent is deleted
	protected void takeDown() {
		// remove agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Find the customer agents
	private class FindCustomersBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Customer");
			customerTemplate.addServices(sd);
			try {
				DFAgentDescription[] customerAgents = DFService.search(myAgent, customerTemplate);
				int size = customerAgents.length;
				customersAID = new AID[size];
				for(int i = 0; i < size; i++) {
					customersAID[i] = customerAgents[i].getName();
				}
			} catch(FIPAException e) {
				e.printStackTrace();
			}
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
	
	// Find the dayCoordinator agent
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
	
	// Behaviour that waits for new day or end simulation calls
	public class DayCoordinatorWaiterBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(dayCoordinatorAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					// Each new day, carry out different orders
					if(ce instanceof NextDay) {
						// Add sequential behaviour
						SequentialBehaviour dailyActivity = new SequentialBehaviour();
						dailyActivity.addSubBehaviour(new ProcessOrderBehaviour());
						dailyActivity.addSubBehaviour(new ProcessOrdersReadyBehaviour());
						dailyActivity.addSubBehaviour(new CalculateDailyProfitBehaviour());
						dailyActivity.addSubBehaviour(new EndDayBehaviour());
						myAgent.addBehaviour(dailyActivity);
					} else {
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
	
	// agree or refuse to process an order
	private class ProcessOrderBehaviour extends Behaviour{
		int step = 0;
		int ordersReceived = 0;
		Order currentOrder;
		
		public void action() {
			switch(step) {
			// Receive a sell order request message from a customer
			case 0:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof Action) {
							Concept action = ((Action)ce).getAction();
							if(action instanceof SellOrder) {
								SellOrder sellOrder = (SellOrder) action;
								// Create agent action
								CalOrderCost calculateOrderCost = new CalOrderCost();
								calculateOrderCost.setOrder(sellOrder.getOrder());
								// Create wrapper
								Action request = new Action();
								request.setAction(calculateOrderCost);
								request.setActor(warehouseAID);
								// Request warehouse to calculate order cost
								ACLMessage costRequestMsg = new ACLMessage(ACLMessage.REQUEST);
								costRequestMsg.addReceiver(warehouseAID);
								costRequestMsg.setLanguage(codec.getName());
								costRequestMsg.setOntology(ontology.getName());
								try {
									// Convert java objects to strings
									getContentManager().fillContent(costRequestMsg, request);
									myAgent.send(costRequestMsg);
								} catch (CodecException codece) {
									codece.printStackTrace();
								} catch (OntologyException oe) {
									oe.printStackTrace();
								}
								currentOrder = sellOrder.getOrder();
								step++;
								ordersReceived++;
							}
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Receive cost from warehouse agent
			case 1:
				MessageTemplate mt1 = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt1);
				if( msg1 != null) {
					try {
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof PredictedCost) {
							PredictedCost costs = (PredictedCost) ce;
							// Calculate profit/ loss from cost and sell price
							int sellPrice = currentOrder.getUnitPrice() * currentOrder.getQuantity();
							int benefit = sellPrice - costs.getCost();
							// Decide whether to accept or decline the order
							float benefitMargin = ((float)benefit / (float)sellPrice) * 100.0f;
							if(benefitMargin >= minimumBenefitMargin) {
								// Create action
								PrepairOrderAssembly orderToAssemble = new PrepairOrderAssembly();
								orderToAssemble.setOrder(currentOrder);
								// Create wrapper
								Action request = new Action();
								request.setAction(orderToAssemble);
								request.setActor(warehouseAID);
								// Request warehouse to prepare the order assembly
								ACLMessage prepareOrderRequestMsg = new ACLMessage(ACLMessage.REQUEST);
								prepareOrderRequestMsg.addReceiver(warehouseAID);
								prepareOrderRequestMsg.setLanguage(codec.getName());
								prepareOrderRequestMsg.setOntology(ontology.getName());
								try {
									// Transform java objects to strings
									getContentManager().fillContent(prepareOrderRequestMsg, request);
									myAgent.send(prepareOrderRequestMsg);
								} catch (CodecException codece) {
									codece.printStackTrace();
								} catch (OntologyException oe) {
									oe.printStackTrace();
								}
							}
							// Check if there are more orders to receive
							if(ordersReceived == customersAID.length) {
								step++;
							} else {
								step = 0;
							}
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Send notification to warehouse, no new orders
			case 2:
				// Create predicate
				NoMoreOrders  noMoreOrdersToday = new NoMoreOrders();
				// Send message to warehouse
				ACLMessage noMoreOrdersInformMsg = new ACLMessage(ACLMessage.INFORM);
				noMoreOrdersInformMsg.addReceiver(warehouseAID);
				noMoreOrdersInformMsg.setLanguage(codec.getName());
				noMoreOrdersInformMsg.setOntology(ontology.getName());
				try {
					// Transform java objects to string
					getContentManager().fillContent(noMoreOrdersInformMsg, noMoreOrdersToday);
					myAgent.send(noMoreOrdersInformMsg);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			}
		}
		
		public boolean done() {
			return step == 3;
		}
	}
	
	// Receive the orders ready from the warehouse,
	// send them to the customers
	// and take payment
	private class ProcessOrdersReadyBehaviour extends Behaviour{
		private int step = 0;
		private int numbOrdersReadyDelivered = 0;
		private int numbPaymentsReceived = 0;
		
		public void action() {
			switch(step) {
			// Receive orders ready from warehouse
			case 0:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						// Transform string to java objects
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof OrderReady) {
							OrderReady ordersReady = (OrderReady) ce;
							// Send ready orders
							if(ordersReady.getOrders() == null || ordersReady.getOrders().isEmpty()) {
								step = 2;
							} 
							else
							{
								for(Order orderReady : ordersReady.getOrders()) {
									// Create predicate
									OrderDelivered orderDelivered = new OrderDelivered();
									orderDelivered.setOrder(orderReady);
									// Send order ready to customer
									ACLMessage orderDeliveredMsg = new ACLMessage(ACLMessage.INFORM);
									orderDeliveredMsg.addReceiver(orderReady.getAID());
									orderDeliveredMsg.setLanguage(codec.getName());
									orderDeliveredMsg.setOntology(ontology.getName());
									try {
										// Transform java objects to string
										getContentManager().fillContent(orderDeliveredMsg, orderDelivered);
										myAgent.send(orderDeliveredMsg);
									} catch (CodecException codece) {
										codece.printStackTrace();
									} catch (OntologyException oe) {
										oe.printStackTrace();
									}
								}
								numbOrdersReadyDelivered = ordersReady.getOrders().size();
								step = 1;
							}
						} else {
							myAgent.postMessage(msg);
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			// Receive payments
			case 1:
				MessageTemplate mt1 = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt1);
				if(msg1 != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof Payment) {
							// Add payment to daily payments
							Payment payment = (Payment) ce;
							dailyPayments += payment.getAmount();
							numbPaymentsReceived++;
							// When all payments are received increase step
							if(numbOrdersReadyDelivered == numbPaymentsReceived) {
								step++;
							}
						}
					}  catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			}
		}
		
		public boolean done() {
			return step == 2;
		}
	}
	
	// Finish the calculation of the daily profit,
	// calculate gross profit
	private class CalculateDailyProfitBehaviour extends Behaviour{
		private int step = 0;
		
		public void action() {
			switch(step) {
			// Get all costs from all agents
			case 0:
				// Create predicate
				TodaysExpenses warehouseExpensesToday = new TodaysExpenses();
				// Asks for costs
				ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
				msg.addReceiver(warehouseAID);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				try {
					// Transform java objects to strings
					getContentManager().fillContent(msg, warehouseExpensesToday);
					myAgent.send(msg);
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;
				break;
			// Receive warehouse costs
			case 1:
				MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage msg1 = myAgent.receive(mt);
				if(msg1 != null) {
					try {
						// Transform strings to java objects
						ContentElement ce = getContentManager().extractContent(msg1);
						if(ce instanceof Expenses) {
							// Get storage cost
							Expenses expenses = (Expenses) ce;
							dailyPurchasesCost = expenses.getSuppliesCost();
							dailyPenaltiesCost = expenses.getPenaltiesCost();
							dailyWarehouseStorageCost = expenses.getStorageCost();
							step++;
						}
					} catch (CodecException codece) {
						codece.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				}
				break;
			// Calculate daily profit
			case 2:
				dailyProfit = dailyPayments - dailyPenaltiesCost - dailyWarehouseStorageCost - dailyPurchasesCost;
				totalProfit += dailyProfit;
				System.out.println("Daily profit of: " + dailyProfit);
				System.out.println("Total profit of: " + totalProfit);
				step++;
				break;
			}
		}
		
		public boolean done() {
			return step == 3;
		}
	}
	
	// Call the day off
	private class EndDayBehaviour extends OneShotBehaviour{
		public void action() {
			// Reset daily variables
			dailyProfit = 0;
			dailyPurchasesCost = 0;
			dailyPenaltiesCost = 0;
			dailyWarehouseStorageCost = 0;
			dailyPayments = 0;
			// Create predicate
			EndDay dayEnd = new EndDay();
			// Send day end message to timer
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(dayCoordinatorAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Transform java objects to strings
				getContentManager().fillContent(msg, dayEnd);
				myAgent.send(msg);
			} catch (CodecException codece) {
				codece.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
		}
	}
	

}
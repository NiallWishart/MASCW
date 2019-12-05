package supplyChainOntology.concepts;



public class Phablet extends SmartPhone{
	
	// Phablet constant variables
	public Phablet() {
		Screen screen = new Screen();
		screen.setSize("7");
		this.setScreen(screen);
		Battery battery = new Battery();
		battery.setCharge("3000");
		this.setBattery(battery);
	}
}

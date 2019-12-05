package supplyChainOntology.concepts;


public class SmallSmartphone extends SmartPhone{
	
	// Small smart phones constant variables
	public SmallSmartphone() {
		Screen screen = new Screen();
		screen.setSize("5");
		this.setScreen(screen);
		Battery battery = new Battery();
		battery.setCharge("2000");
		this.setBattery(battery);
	}
}

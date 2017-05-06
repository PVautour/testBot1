import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

	private Mirror mirror = new Mirror();

	private Game game;

	private Player self;
	
	private int supplyCheck = 0;

	public void run() {
		mirror.getModule().setEventListener(this);
		mirror.startGame();
	}

	@Override
	public void onUnitCreate(Unit unit) {
		System.out.println("New unit discovered " + unit.getType());
	}

	@Override
	public void onStart() {
		game = mirror.getGame();
		self = game.self();

		// Use BWTA to analyze map
		// This may take a few minutes if the map is processed first time!
		System.out.println("Analyzing map...");
		BWTA.readMap();
		BWTA.analyze();
		System.out.println("Map data ready");

		int i = 0;
		for (BaseLocation baseLocation : BWTA.getBaseLocations()) {
			System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
			for (Position position : baseLocation.getRegion().getPolygon().getPoints()) {
				System.out.print(position + ", ");
			}
			System.out.println();
		}

	}

	@Override
	public void onFrame() {
		// game.setTextSize(10);
		game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());
		boolean supplyChecked = false;
		StringBuilder units = new StringBuilder("My units:\n");

		// iterate through my units
		for (Unit myUnit : self.getUnits()) {
			units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

			// if there's enough minerals, train an SCV
			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50) {
				myUnit.train(UnitType.Terran_SCV);
			}
			// Verifier supply si non-verifie
			if(!supplyChecked){
				supplyChecked = checkSupply(myUnit);
			}	
			// if it's a worker and it's idle, send it to the closest mineral
			// patch
			
			if (myUnit.getType().isWorker() && myUnit.isIdle()) {
				Unit closestMineral = null;

				// find the closest mineral
				for (Unit neutralUnit : game.neutral().getUnits()) {
					if (neutralUnit.getType().isMineralField()) {
						if (closestMineral == null
								|| myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
							closestMineral = neutralUnit;
						}
					}
				}

				// if a mineral patch was found, send the worker to gather it
				if (closestMineral != null) {
					myUnit.gather(closestMineral, false);
				}
			}
		}

		// draw my units on screen
		game.drawTextScreen(10, 25, units.toString());
	}

	private boolean checkSupply(Unit myUnit) {
		++supplyCheck;
		if (supplyCheck%15 == 0 && myUnit.getType().isWorker() && self.supplyTotal() <= self.supplyUsed()+2 && self.minerals() >= 100 && 1 > self.incompleteUnitCount(UnitType.Terran_Supply_Depot)) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Supply_Depot, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Supply_Depot, emplacement);
		}	
		return true;
	}
	
	private void checkMarines(Unit myUnit){
//					// Construit les barracks
//					if(self.completedUnitCount(UnitType.Terran_Supply_Depot) == 1 && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0){
//						SavePourBarrack = true;
//					}
//					if(supplyCheck%17 == 0 && SavePourBarrack && self.minerals() >= 150 && myUnit.getType().isWorker() && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0){
//						TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Barracks, myUnit.getTilePosition());
//						myUnit.build(UnitType.Terran_Barracks, emplacement);
//					}
//					if(self.incompleteUnitCount(UnitType.Terran_Barracks) > 0){
//						SavePourBarrack = false;
//					}
//					//Construit les marines
//					if (myUnit.getType() == UnitType.Terran_Barracks && self.minerals() >= 50 &&  !(self.supplyTotal() <= self.supplyUsed()+1)){
//						myUnit.train(UnitType.Terran_Marine);				
//					}
	}
	public static void main(String[] args) {
		new TestBot1().run();
	}
}
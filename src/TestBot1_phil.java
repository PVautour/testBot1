import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

	private Mirror mirror = new Mirror();

	private Game game;

	private Player self;
	private Player Ennemy;
	private boolean SavePourBarrack;
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
		SavePourBarrack = false;
		Ennemy = game.enemy();
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

		// Iteration pour chaque units
		for (Unit myUnit : self.getUnits()) {
			units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");

			// Construit des travailleurs
			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50 && !((self.supplyTotal()-3) <= self.supplyUsed()+1) && 21>self.allUnitCount(UnitType.Terran_SCV)&& !SavePourBarrack) {
				myUnit.train(UnitType.Terran_SCV);
			}

			// Construit les barracks
			if(self.completedUnitCount(UnitType.Terran_Supply_Depot) == 1 && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0){
				SavePourBarrack = true;
			}
			if(supplyCheck%17 == 0 && SavePourBarrack && self.minerals() >= 150 && myUnit.getType().isWorker() && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0){
				TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Barracks, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Barracks, emplacement);
			}
			if(self.incompleteUnitCount(UnitType.Terran_Barracks) > 0){
				SavePourBarrack = false;
			}
			//Construit les marines
			if (myUnit.getType() == UnitType.Terran_Barracks && self.minerals() >= 50 &&  !(self.supplyTotal() <= self.supplyUsed()+1)){
				myUnit.train(UnitType.Terran_Marine);				
			}
			// Verifie si on doit construire des Supply
			checkSupply(myUnit);
			//G�re les attaques
			AttaqueMarines(myUnit);
			//Verifie si un Worker fait rien
			if (myUnit.getType().isWorker() && myUnit.isIdle()) {
				Unit closestMineral = null;

				// Trouve les mineraux les plus pres
				for (Unit neutralUnit : game.neutral().getUnits()) {
					if (neutralUnit.getType().isMineralField()) {
						if (closestMineral == null
								|| myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
							closestMineral = neutralUnit;
						}
					}
				}
				// Si mineraux trouves, envois les travailleurs les ramasser
				if (closestMineral != null) {
					myUnit.gather(closestMineral, false);
				}
			}
		}// FIN Iteration pour chaque units
		// draw my units on screen
		game.drawTextScreen(10, 25, units.toString());
	}
	private void AttaqueMarines( Unit myUnit){
		if(myUnit.getType().equals(UnitType.Terran_Marine)){
			Unit closestEnnemy = null;
			for (Unit EnnemyUnit : Ennemy.getUnits()) {
				if(EnnemyUnit.isVisible(self) && EnnemyUnit.isDetected() && myUnit.getDistance(EnnemyUnit) < myUnit.getDistance(closestEnnemy)){
					closestEnnemy = EnnemyUnit;
				}
			}
			if(closestEnnemy.isVisible(self) && closestEnnemy.isDetected()){
			myUnit.attack(closestEnnemy.getPosition(), false);
			}
		}
	}
	
	private void checkSupply(Unit myUnit) {
		++supplyCheck;
		if (supplyCheck%17 == 0 && myUnit.getType().isWorker() && (self.supplyTotal()-3) <= self.supplyUsed()+1 && self.minerals() >= 100 && 0 == self.incompleteUnitCount(UnitType.Terran_Supply_Depot)) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Supply_Depot, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Supply_Depot, emplacement);
		}	
	}

	public static void main(String[] args) {
		new TestBot1().run();
	}
}
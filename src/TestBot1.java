import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

	private Mirror mirror = new Mirror();

	private Game game;
	private Unit LaRafinery;
	private Player self;
	private Player Ennemy;
	private int TempsDepuisScan = 0;
	private boolean SavePourBarrack;
	private int NbWorkerGaz = 0;
	private int buildingTimer = 0;
	private int supplyCheckTimer = 0;

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
			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50
					&& 21 > self.allUnitCount(UnitType.Terran_SCV) && !SavePourBarrack && !myUnit.isTraining()) {
				myUnit.train(UnitType.Terran_SCV);
			}

			// Construit les barracks
			if (self.completedUnitCount(UnitType.Terran_Supply_Depot) == 1
					&& self.incompleteUnitCount(UnitType.Terran_Barracks) == 0 && ++buildingTimer % 17 == 0) {
				SavePourBarrack = true;
			}
			if (supplyCheckTimer % 3061 == 0 && SavePourBarrack && self.minerals() >= 150 && myUnit.getType().isWorker()
					&& self.incompleteUnitCount(UnitType.Terran_Barracks) == 0) {
				TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Barracks, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Barracks, emplacement);
			}
			if (self.incompleteUnitCount(UnitType.Terran_Barracks) > 0) {
				SavePourBarrack = false;
			}
			// Construit les extracteurs
			ConstruitExtracteurGaz(myUnit);
			// Construit Academie
			ConsrtuitAcademy(myUnit);
			// Decide quels Units construire
			ReseauBayesienDecisionTrainUnit();
			// Construit et gere le scan
			ConsrtuitETgereScan(myUnit);
			// Construit Factory
			ConsrtuitFactory(myUnit);
			// Verifie si on doit construire des Supply
			if (!supplyChecked) {
				checkSupply(myUnit);
				supplyChecked = true;
			}
			// Gère les attaques
			AttaqueUnits(myUnit);
			// Verifie si un Worker fait rien
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
		} // FIN Iteration pour chaque units
			// draw my units on screen
		game.drawTextScreen(10, 25, units.toString());
	}

	private void AttaqueUnits(Unit myUnit) {
		Unit closestTarget = null;
		if (!myUnit.getType().isWorker() && myUnit.canAttack()) {
			for (Unit e : Ennemy.getUnits()) {
				if ((closestTarget == null || e.getDistance(myUnit) < closestTarget.getDistance(myUnit))) {
					closestTarget = e;
				}
			}
			if (closestTarget != null) {// && supplyCheckTimer % 1009 == 0
				myUnit.attack(closestTarget, false);
			}
		} else if (myUnit.getType() == UnitType.Terran_Medic) {
			for (Unit e : self.getUnits()) {
				if ((closestTarget == null || e.getDistance(myUnit) < closestTarget.getDistance(myUnit)
						&& closestTarget.getHitPoints() < closestTarget.getInitialHitPoints()
						&& (closestTarget.getType() == UnitType.Terran_Medic
								|| closestTarget.getType() == UnitType.Terran_Firebat
								|| closestTarget.getType() == UnitType.Terran_Marine))) {
					closestTarget = e;
				}
			}
			if (closestTarget != null) {
				myUnit.useTech(TechType.Healing, closestTarget);
			}
		} else if (!myUnit.getType().isWorker() && supplyCheckTimer % 1009 == 0) {
			Random randomScout = new Random();
			myUnit.attack(game.getAllRegions().get(randomScout.nextInt(game.getAllRegions().size())).getCenter());
		}
	}

	private void ReseauBayesienDecisionTrainUnit() {
		// declaration
		UnitType UnitChoisi = null;
		double HighestProb = 0;
		double p_Marine = Math.random();
		double p_Firebat = Math.random() + 0.1;
		double p_Medic = Math.random();
		double p_Vulture = Math.random();
		double p_Tank = Math.random();
		System.out.println("TIME: " + game.elapsedTime());

		// Facteurs qui influence les probabilités
		// Marines
		if (self.gas() < 50) {
			p_Marine += 0.5;
		}
		if (self.allUnitCount(UnitType.Terran_Marine) < self.allUnitCount(UnitType.Terran_Firebat)) {
			p_Marine += 0.3;
		}
		// Firebats
		if (Ennemy.getRace() == Race.Zerg || Ennemy.getRace() == Race.Protoss) {
			p_Firebat += 0.15;
		}
		if (game.elapsedTime() > 1250) {
			p_Firebat -= 0.3;
		}
		// Medic
		if (5 > self.allUnitCount(UnitType.Terran_Medic) && 10 < self.allUnitCount(UnitType.Terran_Marine)) {
			p_Medic += 0.3;
		}
		if (5 < self.allUnitCount(UnitType.Terran_Medic)) {
			p_Medic -= 0.4;
		}
		// Save Factory
		if (self.gas() < 120 && self.allUnitCount(UnitType.Terran_Factory) == 0) {
			p_Firebat -= 0.3;
			p_Medic -= 0.3;
		}
		// Vulture
		if (self.allUnitCount(UnitType.Terran_Factory) > 0) {
			p_Vulture += 0.1;
		} else {
			p_Vulture = 0;
		}
		if (self.gas() < 50) {
			p_Vulture += 0.2;
		}
		// Tank
		if (self.allUnitCount(UnitType.Terran_Factory) > 0) {
			p_Tank += 0.1;
		} else {
			p_Tank = 0;
		}
		if (game.elapsedTime() > 1100) {
			p_Tank += 0.2;
		}
		// calcul du plus élevé
		// System.out.println("Marine: "+p_Marine);
		// System.out.println("Firebat: "+p_Firebat);
		if (p_Marine > HighestProb) {
			HighestProb = p_Marine;
			UnitChoisi = UnitType.Terran_Marine;
		}
		if (p_Firebat > HighestProb) {
			HighestProb = p_Firebat;
			UnitChoisi = UnitType.Terran_Firebat;
		}
		if (p_Medic > HighestProb) {
			HighestProb = p_Medic;
			UnitChoisi = UnitType.Terran_Medic;
		}
		if (p_Vulture > HighestProb) {
			HighestProb = p_Vulture;
			UnitChoisi = UnitType.Terran_Vulture;
		}
		if (p_Tank > HighestProb) {
			HighestProb = p_Tank;
			UnitChoisi = UnitType.Terran_Siege_Tank_Tank_Mode;
		}
		// Construit les Units
		for (Unit myUnit : self.getUnits()) {

			if (UnitChoisi.equals(UnitType.Terran_Marine) && myUnit.getType() == UnitType.Terran_Barracks
					&& self.minerals() >= 50
					&& !(self.supplyTotal() <= self.supplyUsed() + 1 && !myUnit.isTraining())) {
				myUnit.train(UnitType.Terran_Marine);
			}
			if (UnitChoisi.equals(UnitType.Terran_Firebat) && myUnit.getType() == UnitType.Terran_Barracks
					&& self.gas() >= 25 && self.minerals() >= 50
					&& !(self.supplyTotal() <= self.supplyUsed() + 1 && !myUnit.isTraining())) {
				myUnit.train(UnitType.Terran_Firebat);
			}
			if (UnitChoisi.equals(UnitType.Terran_Medic) && myUnit.getType() == UnitType.Terran_Barracks
					&& self.gas() >= 25 && self.minerals() >= 50
					&& !(self.supplyTotal() <= self.supplyUsed() + 1 && !myUnit.isTraining())) {
				myUnit.train(UnitType.Terran_Medic);
			}
			if (UnitChoisi.equals(UnitType.Terran_Vulture) && myUnit.getType() == UnitType.Terran_Factory
					&& self.minerals() >= 75 && !(self.supplyTotal() <= self.supplyUsed() + 1 && !myUnit.isTraining()
							&& self.allUnitCount(UnitType.Terran_Machine_Shop) > 0)) {
				myUnit.train(UnitType.Terran_Vulture);
			}
			if (UnitChoisi.equals(UnitType.Terran_Siege_Tank_Tank_Mode) && myUnit.getType() == UnitType.Terran_Factory
					&& self.minerals() >= 150 && self.gas() >= 100 && !(self.supplyTotal() <= self.supplyUsed() + 1
							&& !myUnit.isTraining() && self.allUnitCount(UnitType.Terran_Machine_Shop) > 0)) {
				myUnit.train(UnitType.Terran_Siege_Tank_Tank_Mode);
			}
		}
	}

	private void ConsrtuitAcademy(Unit myUnit) {
		if (supplyCheckTimer % 1009 == 0 && myUnit.getType().isWorker()
				&& self.incompleteUnitCount(UnitType.Terran_Academy) == 0
				&& self.completedUnitCount(UnitType.Terran_Academy) == 0
				&& 0 < self.completedUnitCount(UnitType.Terran_Barracks) && self.minerals() >= 150) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Academy, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Academy, emplacement);
		}

	}

	private void ConsrtuitETgereScan(Unit myUnit) {
		if (myUnit.getType().equals(UnitType.Terran_Command_Center)
				&& self.incompleteUnitCount(UnitType.Terran_Comsat_Station) == 0
				&& self.completedUnitCount(UnitType.Terran_Comsat_Station) == 0
				&& 0 < self.completedUnitCount(UnitType.Terran_Academy) && self.minerals() >= 50
				&& !myUnit.isConstructing()) {
			// TilePosition emplacement =
			// game.getBuildLocation(UnitType.Terran_Comsat_Station,
			// myUnit.getInitialTilePosition());
			myUnit.buildAddon(UnitType.Terran_Comsat_Station);
		}
		if (myUnit.getType().equals(UnitType.Terran_Comsat_Station)) {
			Unit closestInvisibleEnnemy = null;
			for (Unit e : Ennemy.getUnits()) {
				System.out.println("for each");
				if ((e.isCloaked() || e.isBurrowed()) && (closestInvisibleEnnemy == null
						|| e.getDistance(myUnit) < closestInvisibleEnnemy.getDistance(myUnit))) {
					System.out.println("set e");
					closestInvisibleEnnemy = e;
				}
			}

			if (closestInvisibleEnnemy != null && myUnit.getEnergy() >= 50
					&& (TempsDepuisScan + 30) < game.elapsedTime()) {
				myUnit.useTech(TechType.Scanner_Sweep, closestInvisibleEnnemy.getPosition());
				TempsDepuisScan = game.elapsedTime();
			}

		}

	}

	private void ConsrtuitFactory(Unit myUnit) {
		if (supplyCheckTimer % 1009 == 0 && myUnit.getType().isWorker()
				&& self.incompleteUnitCount(UnitType.Terran_Factory) == 0
				&& self.completedUnitCount(UnitType.Terran_Factory) == 0
				&& 0 < self.completedUnitCount(UnitType.Terran_Barracks) && self.minerals() >= 200
				&& self.gas() > 100) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Academy, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Factory, emplacement);
		}
		if (supplyCheckTimer % 1009 == 0 && myUnit.getType().isWorker()
				&& self.incompleteUnitCount(UnitType.Terran_Factory) == 0
				&& self.completedUnitCount(UnitType.Terran_Factory) >= 1
				&& 0 < self.completedUnitCount(UnitType.Terran_Barracks) && self.minerals() >= 1000
				&& self.gas() > 100) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Academy, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Factory, emplacement);
		}
		if (myUnit.getType().equals(UnitType.Terran_Factory)
				&& self.incompleteUnitCount(UnitType.Terran_Machine_Shop) == 0
				&& self.completedUnitCount(UnitType.Terran_Machine_Shop) == 0 && self.minerals() >= 50
				&& self.gas() >= 50) {
			myUnit.cancelTrain();
			myUnit.buildAddon(UnitType.Terran_Machine_Shop);
		}
	}

	private void ConstruitExtracteurGaz(Unit myUnit) {
		if (myUnit.getType().isRefinery()) {
			LaRafinery = myUnit;
		}else if (supplyCheckTimer % 1009 == 0 && myUnit.getType().isWorker()
				&& self.completedUnitCount(UnitType.Terran_Refinery) == 0
				&& self.incompleteUnitCount(UnitType.Terran_Refinery) == 0
				&& self.completedUnitCount(UnitType.Terran_Barracks) >= 1) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Refinery, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Refinery, emplacement);
		}
		NbWorkerGaz = 0;
		if (myUnit.getType().isWorker() && myUnit.isIdle()) {
			for (Unit myUnitWorker : self.getUnits()) {
				if (myUnitWorker.isCarryingGas() || myUnitWorker.isGatheringGas()) {
					NbWorkerGaz += 1;
				}
			}
			if (NbWorkerGaz < 3 && self.completedUnitCount(UnitType.Terran_Refinery) == 1) {
				myUnit.gather(LaRafinery, false);
			}
		}
	}

	private void checkSupply(Unit myUnit) {
		++supplyCheckTimer;
		if (myUnit.getType().isWorker() && self.supplyTotal() - 5 <= self.supplyUsed() && self.minerals() >= 100) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Supply_Depot, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Supply_Depot, emplacement);
		}
	}

	public static void main(String[] args) {
		new TestBot1().run();
	}
}

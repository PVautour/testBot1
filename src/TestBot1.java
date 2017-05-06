import java.util.ArrayList;
import java.util.Collections;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

	private Mirror mirror = new Mirror();

	private Game game;
	private Unit LaRafinery;
	private Player self;
	private Player Ennemy;
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
			if (myUnit.getType() == UnitType.Terran_Command_Center && self.minerals() >= 50 && 21>self.allUnitCount(UnitType.Terran_SCV) && !SavePourBarrack) {
				myUnit.train(UnitType.Terran_SCV);
			}

			// Construit les barracks
			if(self.completedUnitCount(UnitType.Terran_Supply_Depot) == 1 && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0 && ++buildingTimer%17 == 0){
				SavePourBarrack = true;
			}
			if(supplyCheckTimer%31 == 0 && SavePourBarrack && self.minerals() >= 150 && myUnit.getType().isWorker() && self.incompleteUnitCount(UnitType.Terran_Barracks) == 0){
				TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Barracks, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Barracks, emplacement);
			}
			if(self.incompleteUnitCount(UnitType.Terran_Barracks) > 0){
				SavePourBarrack = false;
			}
			//Construit les extracteurs
			ConstruitExtracteurGaz(myUnit);
			//Construit Academie
			ConsrtuitAcademy(myUnit);
			//Decide quels Units construire
			ReseauBayesienDecisionTrainUnit();
			// Verifie si on doit construire des Supply
			if(!supplyChecked){
			checkSupply(myUnit);
			supplyChecked = true;
			}	
			//Gère les attaques
			AttaqueUnits(myUnit);
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

	private void AttaqueUnits(Unit myUnit){
		if(myUnit.getType().canAttack() && myUnit.isIdle()){
			Unit closestEnnemy = null;	
			for(Unit e : Ennemy.getUnits()){
				System.out.println("for each");
				if((closestEnnemy == null || e.getDistance(myUnit) < closestEnnemy.getDistance(myUnit))){
					System.out.println("set e");
					closestEnnemy = e;
				}
			}
			System.out.println("attack");
			if(closestEnnemy != null){
			myUnit.attack(closestEnnemy, false);	
			}else{
			//	myUnit.move();
			}
		}
	}
	private void ReseauBayesienDecisionTrainUnit(){
		//declaration
		UnitType UnitChoisi = null;
		double HighestProb = 0;
		double p_Marine= Math.random();
		double p_Firebat= Math.random()+0.1;
		//game.elapsedTime()
		//Facteurs qui influence les probabilités
		
		if(Ennemy.getRace() == Race.Zerg || Ennemy.getRace() == Race.Protoss){
			p_Firebat +=0.15;
		}
		if(self.gas() < 50){
			p_Marine +=0.5;
		}
		//calcul du plus élevé
		System.out.println("Marine: "+p_Marine);
		System.out.println("Firebat: "+p_Firebat);
		if(p_Marine >HighestProb){HighestProb = p_Marine; UnitChoisi = UnitType.Terran_Marine;}
		if(p_Firebat >HighestProb){HighestProb = p_Firebat;UnitChoisi = UnitType.Terran_Firebat;}
		//Construit les Units
		for (Unit myUnit : self.getUnits()) {

		if (UnitChoisi.equals(UnitType.Terran_Marine) && myUnit.getType() == UnitType.Terran_Barracks && self.minerals() >= 50 &&  !(self.supplyTotal() <= self.supplyUsed()+1 && !myUnit.isTraining())){
			myUnit.train(UnitType.Terran_Marine);				
		}
		if (UnitChoisi.equals(UnitType.Terran_Firebat) && myUnit.getType() == UnitType.Terran_Barracks && self.gas() >= 25 && self.minerals() >= 50 &&  !(self.supplyTotal() <= self.supplyUsed()+1 && !myUnit.isTraining())){
			myUnit.train(UnitType.Terran_Firebat);				
		}
		}
	}
	private void ConsrtuitAcademy(Unit myUnit){
		if(myUnit.getType().isWorker() && self.incompleteUnitCount(UnitType.Terran_Academy) ==0 && self.completedUnitCount(UnitType.Terran_Academy) ==0 && 0 < self.completedUnitCount(UnitType.Terran_Barracks) && self.minerals() >=150){
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Academy, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Academy, emplacement);
		}

	}
	private void ConstruitExtracteurGaz(Unit myUnit){
		if(myUnit.getType().isRefinery()){
			LaRafinery = myUnit;
		}
		if(myUnit.getType().isWorker() && self.completedUnitCount(UnitType.Terran_Refinery) ==0 && self.incompleteUnitCount(UnitType.Terran_Refinery)==0 && self.incompleteUnitCount(UnitType.Terran_Barracks) == 1){
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Refinery, myUnit.getTilePosition());
			myUnit.build(UnitType.Terran_Refinery, emplacement);
		}
		NbWorkerGaz = 0;
		if(myUnit.getType().isWorker() && myUnit.isIdle() ){
			for (Unit myUnitWorker : self.getUnits()) { if(myUnitWorker.isCarryingGas() || myUnitWorker.isGatheringGas()){NbWorkerGaz+=1;}}
		if(NbWorkerGaz < 2 && self.completedUnitCount(UnitType.Terran_Refinery) ==1 ){
			myUnit.gather(LaRafinery, false);
		}
		}
	}
	private void checkSupply(Unit myUnit) {
		++supplyCheckTimer;
		if (supplyCheckTimer%9 == 0 && myUnit.getType().isWorker() && self.supplyTotal()-4 <= self.supplyUsed() && self.minerals() >= 100 ) {
			TilePosition emplacement = game.getBuildLocation(UnitType.Terran_Supply_Depot, myUnit.getTilePosition());
				myUnit.build(UnitType.Terran_Supply_Depot, emplacement);
		}	
	}

	public static void main(String[] args) {
		new TestBot1().run();
	}
}

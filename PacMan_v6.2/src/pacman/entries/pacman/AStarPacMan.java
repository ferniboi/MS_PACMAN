package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.GameView;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

public class AStarPacMan extends Controller<MOVE> {
	private int safeDist;
	private long computingTime;
	private int currentIndex;
	private int nextIndex;
	private int forbiddenIndex;
	private boolean nextIndexIsSafe;
	private boolean verbose;
	private boolean highlight;
	private ArrayList<Integer> edibleGhostsIndices;
	private ArrayList<Integer> ghostsIndices;
	private ArrayList<Integer> trueGhostsIndices;
	private MOVE myMove = MOVE.NEUTRAL;

	private void startComputing(Game game) {
		this.computingTime = System.currentTimeMillis();
		this.currentIndex = game.getPacmanCurrentNodeIndex();
		this.nextIndexIsSafe = false;
		this.nextIndex = this.currentIndex;
		this.edibleGhostsIndices = new ArrayList<Integer>();
		this.ghostsIndices = new ArrayList<Integer>();
		this.trueGhostsIndices = new ArrayList<Integer>();
	}

	private boolean endComputing(Game game, boolean highlightFobriddenIndex, Color color, String message) {
		if (this.nextIndexIsSafe) {
			if (this.highlight) {
				GameView.addPoints(game, color, game.getShortestPath(this.currentIndex, this.nextIndex));
				if (highlightFobriddenIndex)
					GameView.addPoints(game, Color.white, game.getShortestPath(this.currentIndex, this.forbiddenIndex));
			}
			this.computingTime = System.currentTimeMillis() - this.computingTime;
			if (this.computingTime >= 40)
				System.out.println("time failure" + " -- " + this.computingTime);
			if (this.verbose)
				System.out.println(message + " -- " + this.computingTime);
		}
		return this.nextIndexIsSafe;
	}

	private void setNextIndex(Game game, int targetIndex) {
		if (this.highlight)
			GameView.addPoints(game, Color.gray, game.getShortestPath(this.currentIndex, targetIndex));
		this.nextIndexIsSafe = true;
		this.nextIndex = targetIndex;
	}

	private class Node {
		private int index;
		private int cost;

		public Node(int index, int cost) {
			this.index = index;
			this.cost = cost;
		}

		public int getIndex() {
			return this.index;
		}

		public int getCost() {
			return this.cost;
		}
	}

	private class NodeComparator implements Comparator<Node> {
		public int compare(Node n1, Node n2) {
			if (n1.getCost() < n2.getCost())
				return -1;
			else if (n1.getCost() > n2.getCost())
				return 1;
			return 0;
		}
	}

	private boolean astarPathfinding(Game game) {
		int targetIndex = this.currentIndex;
		HashSet<Integer> discovered = new HashSet<Integer>();
		HashSet<Integer> visited = new HashSet<Integer>();
		PriorityQueue<Node> cost = new PriorityQueue<Node>(new NodeComparator());
		HashMap<Integer, Integer> path = new HashMap<Integer, Integer>();
		int currentIndex = this.currentIndex;
		while (true) {
			visited.add(currentIndex);
			for (int pathIndex : game.getNeighbouringNodes(currentIndex))
				if (!visited.contains(pathIndex) && !discovered.contains(pathIndex)) {
					discovered.add(pathIndex);
					int g = game.getShortestPathDistance(this.currentIndex, pathIndex);
					int h = game.getShortestPathDistance(pathIndex, targetIndex);
					cost.offer(new Node(pathIndex, g + h));
					if (currentIndex == this.currentIndex)
						path.put(pathIndex, pathIndex);
					else
						path.put(pathIndex, path.get(currentIndex));
				}
			if (cost.isEmpty())
				return false;
			Node currentNode = cost.poll();
			currentIndex = currentNode.getIndex();
			if (currentIndex == targetIndex) {
				this.setNextIndex(game, path.get(targetIndex));
				return this.endComputing(game, false, Color.green, "astarPathfinding");
			}
			discovered.remove(currentIndex);
		}
	}

	public MOVE getMove(Game game, long timeDue) {
		this.startComputing(game);

		this.safeDist = 3;
		this.verbose = false;
		this.highlight = false;

		for (GHOST ghost : GHOST.values()) {
			int ghostIndex = game.getGhostCurrentNodeIndex(ghost);
			int dist = game.getShortestPathDistance(this.currentIndex, ghostIndex);
			int edibleTime = game.getGhostEdibleTime(ghost);
			if (edibleTime - dist > this.safeDist) {
				this.edibleGhostsIndices.add(ghostIndex);
				if (this.highlight)
					GameView.addPoints(game, Color.blue, game.getShortestPath(ghostIndex, this.currentIndex));
			} else if (game.getGhostLairTime(ghost) == 0) {
				this.ghostsIndices.add(ghostIndex);
				this.trueGhostsIndices.add(ghostIndex);
				if (this.highlight)
					GameView.addPoints(game, Color.red, game.getShortestPath(ghostIndex, this.currentIndex));
			} else if (game.getGhostLairTime(ghost) > 0) {
				ghostIndex = game.getGhostInitialNodeIndex();
				this.ghostsIndices.add(ghostIndex);
				if (this.highlight)
					GameView.addPoints(game, Color.red, game.getShortestPath(ghostIndex, this.currentIndex));
			}
		}

		if (this.astarPathfinding(game))
			return game.getNextMoveTowardsTarget(this.currentIndex, this.nextIndex, DM.PATH);

		return this.myMove;
	}
}
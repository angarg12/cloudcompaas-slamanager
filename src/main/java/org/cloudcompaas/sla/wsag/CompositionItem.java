package org.cloudcompaas.sla.wsag;

/**
 * @author angarg12
 *
 */
public class CompositionItem {
	private String template;
	private String id;
	private int distance;
	private boolean terminal = false;
	
	public CompositionItem(String template_, String id_, int distance_, boolean terminal_){
		template = template_;
		id = id_;
		distance = distance_;
		terminal = terminal_;
	}

	public String getTemplate() {
		return template;
	}

	public String getId() {
		return id;
	}

	public int getDistance() {
		return distance;
	}

	public boolean isTerminal() {
		return terminal;
	}

	public void setTemplate(String template) {
		this.template = template;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	public void setTerminal(boolean terminal) {
		this.terminal = terminal;
	}
	
}

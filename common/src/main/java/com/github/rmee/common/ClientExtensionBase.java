package com.github.rmee.common;

import groovy.lang.Closure;
import org.gradle.api.Project;

public abstract class ClientExtensionBase {

	protected Project project;

	protected boolean initialized;

	protected Client client;

	protected boolean logCommandLine;

	protected abstract void init();

	protected void checkNotInitialized() {
		if (initialized) {
			throw new IllegalStateException("already initialized, cannot modify anymore");
		}
	}

	public Client getClient() {
		return client;
	}

	public Client client(Closure closure) {
		return (Client) project.configure(client, closure);
	}

	public boolean isLogCommandLine() {
		init();
		return logCommandLine;
	}

	public void setLogCommandLine(Boolean logCommandLine) {
		this.logCommandLine = logCommandLine;
	}
}

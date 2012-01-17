package debugging;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;
import java.util.Vector;

import main.NoiseBot;

import panacea.CircularQueue;
import static panacea.Panacea.*;

/**
 * Debugger
 *
 * @author Michael Mrozek
 *         Created Dec 12, 2010.
 */
public class Debugger {
	private static final int PORT = 41932;
	
	public static Debugger me;
	
	private ServerSocket server;
	Vector<Client> clients;
	CircularQueue<Event> log;
	CircularQueue<String> in, out;
	
	static {
		me = new Debugger();
	}
	
	private Debugger() {
		try {
			this.server = new ServerSocket(PORT);
		} catch(IOException e) {
			System.err.println("Unable to start debugger");
			e.printStackTrace();
		}
		
		this.clients = new Vector<Client>();
		this.log = new CircularQueue<Event>(500);
		this.in = new CircularQueue<String>(50);
		this.out = new CircularQueue<String>(50);

		new Thread(new Runnable() {
			@Override public void run() {
				while(true) {
					try {
						final Client client = new Client(Debugger.this.server.accept());
						clients.add(client);
						System.out.println("Added debugging client: " + client);
					} catch(IOException e) {
						System.err.println("Problem adding client connection");
						e.printStackTrace();
					}
				}
			}
		}, "debugger-connections").start();
	}
	
	@Override protected void finalize() throws Throwable {
		super.finalize();
		for(Client client : this.clients) {
			client.getSocket().close();
		}
		this.server.close();
	}

	public void log(Level level, String className, String methodName, int line, String msg) {
		final Event event = new Event(level, className, methodName, line, msg);
		System.out.println(event);
		
		this.log.add(event);
		this.out(event);
	}
	
	public void log(Level level, String msg) {
		for(StackTraceElement e : Thread.currentThread().getStackTrace()) {
			if(!(e.getClassName().startsWith("debugging.") || e.getClassName().equals("java.lang.Thread"))) {
				log(level, e.getClassName(), e.getMethodName(), e.getLineNumber(), msg);
				return;
			}
		}
	}
	
	void in(Client client, String command) {
		if(!client.isAuthenticated()) {
			if(command.startsWith("auth ")) {
				final String nick = command.substring(5);
				if(nick.equals("") || nick.contains(" ") || nick.startsWith("#")) {
					client.send("Invalid nick");
					return;
				}
				
				final String code;
				{
					final Random rand = new Random();
					final char[] buffer = new char[10];
					for(int i = 0; i < buffer.length; i++) {
						buffer[i] = (char)('0' + rand.nextInt(10));
					}
					code = new String(buffer);
				}
				
				client.startAuthentication(nick, code);
				NoiseBot.me.sendMessage(nick, "Debugger authentication request. Code: " + code);
			} else if(command.startsWith("code ")) {
				final String code = command.substring(5);
				client.authenticate(code);
			} else if(command.equals("sync")) {
				NoiseBot.me.sync();
			} else {
				client.send("Unauthenticated");
			}
			
			return;
		}
		
		Parser.parse(client, command);
	}
	
	void out(Client client, Event msg) {
		if(!client.isAuthenticated()) {return;}
		client.send(msg);
	}
	
	void out(Event msg) {
		for(Client client : this.clients) {
			if(!client.isAuthenticated()) {continue;}
			this.out(client, msg);
		}
	}
	
	void out(Client client, String msg) {
		if(!client.isAuthenticated()) {return;}
		client.send(msg);
	}
	
	void out(Client client, Iterable<?> iter) {
		if(!client.isAuthenticated()) {return;}
		for(Object o : iter) {
			if(o instanceof Event) {
				client.send((Event)o);
			} else {
				client.send(o);
			}
		}
	}
}

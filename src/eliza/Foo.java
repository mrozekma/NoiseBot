package eliza;

public class Foo {
	public static void main(String[] args) {
		final ElizaMain eliza = new ElizaMain();
		eliza.readScript(true, "/tmp/eliza/script");
		System.out.println(eliza.processInput("This is a test"));
		System.out.println(eliza.processInput("What's your name?"));
		System.out.println(eliza.processInput("What's your favorite color?"));
	}
}

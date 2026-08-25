import Engine.DatabaseEngine;
import parser.SQLParser;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        DatabaseEngine db = new DatabaseEngine();
        SQLParser parser = new SQLParser();

        Scanner sc = new Scanner(System.in);

        System.out.println("=================================");
        System.out.println("        Welcome to TinyDB");
        System.out.println("Type EXIT to quit.");
        System.out.println("=================================");

        while (true) {

            System.out.print("TinyDB> ");

            String query = sc.nextLine().trim();

            if (query.equalsIgnoreCase("EXIT")) {
                System.out.println("Goodbye!");
                break;
            }

            if (query.isEmpty()) {
                continue;
            }

            try {
                parser.execute(query, db);
            }
            catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        sc.close();
    }
}
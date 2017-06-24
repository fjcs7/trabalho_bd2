package br.com.trabalhobd2.principal;

import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * 
 * Run numEmployees transactions, where at most maxConcurrent transactions can run in parallel.
 * It is by no means a real application and certainly has a lot of bugs. *Use at your own risk.*
 * Just type java db.ConcurrentTransactions for usage information
 * @author Leonardo Andrade Ribeiro
 *
 */
public class ConcurrentTransactions 
{
	private static int isolationLevel = -1;
	
	private static String table = null;
	
	private static boolean verbose = false;
	
	private static int numEmployees;
        
        private static String driverDeConexao, 
                              stringDeConexao,
                              loginBd,
                              senhaBd;
        
        public ConcurrentTransactions(String driverDeConexao,
                                      String stringDeConexao,
                                      String loginBd,
                                      String senhaBd){
            this.driverDeConexao = driverDeConexao;
            this.stringDeConexao = stringDeConexao;
            this.loginBd = loginBd;
            this.senhaBd = senhaBd;
        }
	
	private static void printHelpMessage()
	{
		System.out.println("params: numEmployees maxConcurrent workload tableName isolationLevel [verbose]");
		System.out.println("workload = { 1 | 2 }");
		System.out.println("isolationLevel = { READ_UNCOMMITED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE | SNAPSHOT } Obs.: SNAPSHOT is only supported by MS SQL Server");
		System.out.println("Example: java db.ConcurrentTransactions 100 10 1 jdbc.account READ_COMMITED verbose");
		
	}
	
    public static void metodoDeEntrada(String[] args) throws SQLException 
    {
        // read command line parameters
    	if (args.length != 5 && args.length != 6) 
        {
                printHelpMessage();
                System.exit(-1);
        }
        
        int maxConcurrent = 0, workload = 0;
        
        try
        {
            numEmployees = Integer.parseInt(args[0]);
            maxConcurrent = Integer.parseInt(args[1]);
            workload = Integer.parseInt(args[2]);       	
        }
        catch (NumberFormatException nfe)
        {
        	System.out.println("Wrong param format. Exception message: " + nfe.getMessage());
        	printHelpMessage();
        	System.exit(-1);
        }

        if (workload != 1 && workload != 2)
        {
			System.out.println(String.format("Unknown workload: %d ", workload));
			printHelpMessage();
			System.exit(-1);       	
        }
        
        table = args[3]; 
        
        String isStr = args[4].toUpperCase(); 
        
        switch (isStr)
		{
			case "READ_UNCOMMITTED":
			{
				isolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED;
				break;
			}
			
			case "READ_COMMITTED":
			{
				isolationLevel = Connection.TRANSACTION_READ_COMMITTED;
				break;
			}
			
			case "REPEATABLE_READ":
			{
				isolationLevel = Connection.TRANSACTION_REPEATABLE_READ;
				break;
			}
			
			case "SERIALIZABLE":
			{
				isolationLevel = Connection.TRANSACTION_SERIALIZABLE;
				break;
			}

			//workaround for using MS SQL Server snapshot isolation level, 
			// which is not defined by java regular Connection constants
			//reference: http://technet.microsoft.com/en-us/library/ms378149.aspx
			
			case "SNAPSHOT":
			{
				isolationLevel = Connection.TRANSACTION_READ_COMMITTED + 4094;
				break;    				
			}
			
			default:
			{
				System.out.println(String.format("Unknown isolation level; %s ", isStr));
				printHelpMessage();
				System.exit(-1);
			}
		}
        
        if (args.length == 6)
        {
        	verbose = Boolean.parseBoolean(args[5]);
        }
        
       // if (workload == 1) {
       
        try
        {
        	populateDatabase();
        }
        catch (SQLException sqe)
        {
        	System.err.println(String.format("Exception caught while populating database. Message: %s", sqe.getMessage()));
        	System.exit(-1);
        }
    //}
        // create numEmployees transactions
        Transaction[] trans = new Transaction[numEmployees];
        for (int i = 0; i < trans.length; i++) 
        {
                trans[i] = new Transaction(i + 1, workload);
        }

        long startTime = System.currentTimeMillis();
        
        // start all transactions using a connection pool
        ExecutorService pool = Executors.newFixedThreadPool(maxConcurrent);
        for (int i = 0; i < trans.length; i++) 
        {
                pool.execute(trans[i]);
        }
        pool.shutdown(); // end program after all transactions are done
        
        
        try 
        {
        	//very conservatively give 5 seconds for each thread to complete 
        	int timeout = trans.length * 5;
			boolean terminated = pool.awaitTermination(timeout, TimeUnit.SECONDS);
			if (!terminated)
			{
				System.err.println(String.format("Execution timeout! Pool of transactions has not terminated after %d seconds", timeout));
				System.exit(-1);
			}
		} 
        catch (InterruptedException e) 
        {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
                 
        long endTime = System.currentTimeMillis();
        
        double executionTime = (endTime - startTime)/1000.0;
        
        int rbCount = 0;
        for (int i = 0; i < trans.length; i++)
        	rbCount += trans[i].rbCount;
        double correctness = getCorrectness();
        System.out.println(String.format("%d transactions executed in %.3f seconds", trans.length, executionTime));
        System.out.println(String.format("Correctness: %s, no of rollbacks: %d", new Double(correctness).toString(), rbCount));
    }

    private static double getCorrectness() throws SQLException
    {
    	Connection conn = getConnection();
    	Statement st = conn.createStatement();
    	String query = "select balance from " + table + " where number = 0"; 
    	ResultSet rs = st.executeQuery(query);
    	rs.next();
    	int balance = rs.getInt(1);
    	return (numEmployees - balance)/((double)numEmployees); 
    }
    
    private static void populateDatabase() throws SQLException
    {
    	String deleteQuery = "delete from " + table;
    	String insertQuery = "insert into " + table + " values (?, ?)";
    	
    	Connection conn = getConnection();
    	conn.setAutoCommit(false);
    	if (verbose)
    		System.out.print("Deleting all tuples from table " + table + " ... ");
    	Statement st = conn.createStatement();
    	st.executeUpdate(deleteQuery);
    	st.close();
    	if (verbose)
    		System.out.println("done");
    	if (verbose)
    		System.out.print("Populating table " + table + " ... ");
    	PreparedStatement ps = conn.prepareStatement(insertQuery);
    	//first insert company's tuple
    	ps.setInt(1, 0);
    	ps.setInt(2, numEmployees);
    	ps.executeUpdate();
    	
    	//now insert tuples of the employees 
    	for (int i = 0; i < numEmployees; i++)
    	{
        	ps.setInt(1, i + 1);
        	ps.setInt(2, 0);
        	ps.executeUpdate();
    	}
    	
    	ps.close();
    	conn.commit();
    	if (verbose)
    		System.out.println("done");
    }
    
  	private static Connection getConnection() throws SQLException
   	{
  		/**
  		 * Change info below to your jdbc environment
  		 */
   		String url = stringDeConexao;
   		//String url = "jdbc:db2://localhost:50000/dbclass";
        try {
            Class.forName(driverDeConexao);
    		//Class.forName("com.ibm.db2.jcc.DB2Driver");
    	} catch (ClassNotFoundException e) {
    		e.printStackTrace();
    		System.err.println("Postgres jdbc driver not found.");
    		//System.err.println("IBM DB2 jdbc driver not found.");
    		System.exit(-1);
    	}
    	Connection conn = DriverManager.getConnection(url, loginBd, senhaBd);
    	//Connection conn = DriverManager.getConnection(url, "db2inst1", "zweiundzwangig");
    	//////////////////////////////////////////////////////////////////////////
    	
    	conn.setTransactionIsolation(isolationLevel);
    	return conn;	
    }
    
  	public static class Transaction extends Thread 
  	{

 	    // identifier of the transaction
  	    int id;
  	    //workload type
  	    int workload;
  	    
  	    //rollbackCount
  	    int rbCount = 0;
  	    Transaction(int id, int workload) 
  	    {
  	         this.id = id;
  	         this.workload = workload;
  	    }

  	    @Override
  	    public void run() 
  	    {
  	    	if (verbose)
  	    		System.out.println("transaction " + id + " started with workload " + workload);
  	    	
  	    	try (Connection conn = ConcurrentTransactions.getConnection())
  			{
  	    		conn.setAutoCommit(false);
  	    		if (workload == 1)
  	    		{
  	  	            String query = "select balance from " + table + " where number = ?";
  	  	            String update = "update " + table + " set balance = ? where number = ?";
  	  	            
  	  	            PreparedStatement queryPs = conn.prepareStatement(query);
  	  	            PreparedStatement updatePs = conn.prepareStatement(update);
  	  	            
  	  	            boolean done = false;
  	  	            
  	  	            while (!done)
  	  	            {
  	  	            	try
  	  	            	{
  	  	            		//System.out.println("transaction " + id + ": sending queries to SGBD");
  	  	            		
  	  	            		//now get company's balance
  	  	            		queryPs.setInt(1, 0);
  	  	            	    ResultSet rs = queryPs.executeQuery();
  	  	            		rs.next();
  	  	            		int balanceComp = rs.getInt(1);
  	  	            		
  	  	            		//update company's balance
  	  	            		balanceComp = balanceComp - 1;
  	  	            		updatePs.setInt(1, balanceComp);
  	  	            	    updatePs.setInt(2, 0);
  	  	            		updatePs.executeUpdate();
  	  	            		
  	  	            		//retrieve balance of the employee account
  	  	            		queryPs.setInt(1, id);
  	  	            		 rs = queryPs.executeQuery();
  	  	            		rs.next();
  	  	            		int balanceEmp = rs.getInt(1);
  	  	            		
  	  	            		//update employee's balance
  	  	            		balanceEmp = balanceEmp + 1;
  	  	            		updatePs.setInt(1, balanceEmp);
  	  	            		updatePs.setInt(2, id);
  	  	            		updatePs.executeUpdate();
  	  	            		
  	  	            		
  	  	            		queryPs.close();
  	  	            		updatePs.close();
  	  	            		done = true;
  	  	            		
  	  	            	}
  	  	            	catch (SQLException sqe)
  	  	            	{
  	  	            		try 
  	  	            		{
  	  	            			if (verbose)
  	  	            				System.out.println(String.format("Transaction is being rolled back. Exception message: %s", sqe.getMessage()));
  	  	            			conn.rollback();
  	  	            			rbCount++;
  	  	            		} 
  	  	            		catch (SQLException excep) 
  	  	            		{
  	  	            			System.err.println("Error while rolling transaction back... we'll give up.");
  	  	            			System.exit(-1);		
  	  	            		}
  	  	            	}
  	  	            	
  	  	            }

  	    		}
  	    		
  	    		else
  	    		{
  	  	            boolean done = false;
  	  	            
  	  	            while (!done)
  	  	            {
	  	  	    			Statement st = conn.createStatement();
  	    	  	            String update1 = "update " + table + " set balance = balance + 1 where number = " + id;
  	    	  	            String update2 = "update " + table + " set balance = balance - 1 where number = " + 0;
  	  	            	try
  	  	            	{
  	  	            	     //System.out.println("transaction " + id + ": sending queries to SGBD");
  	  	            		
  	  	  	  	            st.executeUpdate(update1);
  	    	  	            st.executeUpdate(update2);
  	  	            		
  	  	            		st.close();
  	  	            		
  	  	            		done = true;
  	  	            		
  	  	            	}
  	  	            	catch (SQLException sqe)
  	  	            	{
  	  	            		try 
  	  	            		{
  	  	            			if (verbose)
  	  	            				System.out.println(String.format("Transaction is being rolled back. Exception message: %s", sqe.getMessage()));
  	  	            			conn.rollback();
  	  	            			rbCount++;
  	  	            		} 
  	  	            		catch (SQLException excep) 
  	  	            		{
  	  	            			System.err.println("Error while rolling transaction back... we'll give up. Call the professor.");
  	  	            			System.exit(-1);		
  	  	            		}
  	  	            	}
  	  	            	
  	  	            }
  	    		}
  	    		
	  	        conn.commit();
	  	        if (verbose)
	  	        	System.out.println("transaction " + id + " terminated"); 	    		
  			}
  	    	
			catch (SQLException e) 
			{
				//e.printStackTrace();
				System.err.println(String.format("A database access error has occurred. Message: %s", e.getMessage()));
				System.exit(-1);
			}
  	    	
  	    }
  		
  	}
}





/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jdbcExample;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fjcs7_000
 */
public class Principal {
    public static void main(String []args){
        String argumentos[] = {"1000", "10", "1", "account", "READ_COMMITTED", "true"};
        
        ConcurrentTransactions ct = new ConcurrentTransactions();
        try {
            ct.main(argumentos);
        } catch (SQLException ex) {
            Logger.getLogger(Principal.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}

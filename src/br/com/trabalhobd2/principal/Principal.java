/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.trabalhobd2.principal;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fjcs7_000
 */
public class Principal {
    public static void text(String []args){
        //#Dados de Configuração//
        String url   = "jdbc:postgresql://localhost:5432/trabalhobd2",
               drive = "org.postgresql.Driver",
               login = "postgres",
               senha = "1234";
        //#Dados de Configuração//
        
        //#Dados de Execução//
        Boolean exibirTransacoes = true;
        String numeroDeInsercoes = "1000",
               qtMaxDeTransacoesConcorrentes = "100",
               cargaDeTrabalho = "1",
               nomeDaTabela = "account",
               nivelDeIsolamento = "READ_COMMITTED";
        //#Dados de Execução//
        
        String argumentos[] = {numeroDeInsercoes,
                               qtMaxDeTransacoesConcorrentes, 
                               cargaDeTrabalho,
                               nomeDaTabela, 
                               nivelDeIsolamento,
                               exibirTransacoes.toString()};
        
        ConcurrentTransactions ct = new ConcurrentTransactions(drive, url,login, senha,null);
        try {
            ct.metodoDeEntrada(argumentos);
        } catch (SQLException ex) {
            Logger.getLogger(Principal.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public static void main(String[] args) throws SQLException 
    {
       
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new GUI().setVisible(true);
            }
        }); 
    }
}

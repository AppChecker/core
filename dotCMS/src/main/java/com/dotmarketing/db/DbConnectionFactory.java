
package com.dotmarketing.db;

import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
        POSTGRES, MySQL, MSSQL, ORACLE,H2;
    }
    
    /**
     * Returns if the db is in a transaction - it will not open a db connection
     * if there is not one already open - instead, it will return false
     * @return
     * @throws DotDataException 
     */
    public static boolean startTransactionIfNeeded() throws DotDataException{
        boolean startTransaction = !inTransaction();;

        try {
            if(startTransaction){
                DbConnectionFactory.getConnection().setAutoCommit(false);
            }
        } catch (SQLException e) {
            Logger.error(DbConnectionFactory.class,e.getMessage(),e);
            throw new DotDataException(e.getMessage(),e);
        }
        return startTransaction;
    }
    
    public static void closeAndCommit()  throws DotDataException{
        try {
            if(inTransaction()){
                DbConnectionFactory.getConnection().commit();
            }
            closeConnection();
        } catch (Exception e) {
            throw new DotDataException(e.getMessage(), e);
        }
        
    }
    
    
    public static void rollbackTransaction() throws DotDataException{
        boolean inTransaction = inTransaction();
        try {
            if(inTransaction){
                DbConnectionFactory.getConnection().rollback();
                DbConnectionFactory.getConnection().setAutoCommit(true);
            }
        } catch (SQLException e) {
            Logger.error(DbConnectionFactory.class,e.getMessage(),e);
            throw new DotDataException(e.getMessage(),e);
        }
    }
    
    

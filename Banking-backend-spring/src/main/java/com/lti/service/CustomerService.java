package com.lti.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lti.entity.Account;
import com.lti.entity.AccountDetail;
import com.lti.entity.Registration;
import com.lti.entity.Transaction;
import com.lti.enums.TransactionType;
import com.lti.model.Transactions;
import com.lti.repository.CustomerRepository;
import com.lti.repository.GenericRepository;

@Service
@Transactional
public class CustomerService {

	@Autowired
	private CustomerRepository customerRepository;
	
	@Autowired
	private GenericRepository genericRepository;
	
	//business logic to autogenerated id
	public long register(Registration customer) {
		if(customerRepository.isCustomerPresent(customer.getAadhaarNo()))
			throw new ServiceException("Customer already registered !");
		else {
			Registration updateCustomer = (Registration) customerRepository.save(customer);
			return updateCustomer.getReferenceNo();
		}
	}
	
	public Account login(long customerId, String loginPassword) {
		try {
			long id = customerRepository.fetchIdByLoginIdAndPassword(customerId, loginPassword);
			Account account = customerRepository.find(Account.class, id);
			return account;
		}
		catch(EmptyResultDataAccessException e) {
			throw new ServiceException("Invalid email/password");
		}
	}
	

public String impsTransaction(Transactions transaction) {
		
		try {
			AccountDetail acc1 = genericRepository.find(AccountDetail.class, transaction.getFromAccount());
			AccountDetail acc2 = genericRepository.find(AccountDetail.class, transaction.getToAccount());
			
			//setting the senders account 
			if(acc1.getBankBalance()<transaction.getAmount()) {
				return null;
			}
			else {
				if(transaction.getAmount()<10000.00) {
					acc1.setBankBalance(acc1.getBankBalance()-transaction.getAmount()-2.50);
				}
				else if(transaction.getAmount()<100000.00) {
					acc1.setBankBalance(acc1.getBankBalance()-transaction.getAmount()-5.00);
				}
				else if(transaction.getAmount()<200000.00) {
					acc1.setBankBalance(acc1.getBankBalance()-transaction.getAmount()-15.00);
				}
				else {
					throw new ServiceException("Amount more than 2 lacs cannot be transferred using IMPS");
				}
				
				genericRepository.save(acc1);
				
				Transaction trans1 = new Transaction();
				
				trans1.setFromAccount(acc1);
				trans1.setToAccount(acc2);
				trans1.setAmount(transaction.getAmount());
				trans1.setModeOfTransaction(TransactionType.IMPS);
				trans1.setRemarks("Debited for "+transaction.getRemarks());
				trans1.setTransactionDate(LocalDateTime.now());
				trans1.setMessage(transaction.getMessage());
				
				List<Transaction> t1 = new ArrayList<Transaction>();
				t1.add(trans1);
				
				acc1.setFromTransactions(t1);
				acc2.setToTransactions(t1);
				
				Transaction trans1RefNo = (Transaction) genericRepository.save(trans1);
				
				//setting the receiver's account
				acc2.setBankBalance(acc2.getBankBalance()+transaction.getAmount());
				
				genericRepository.save(acc2);
				
				Transaction trans2 = new Transaction();
				trans2.setFromAccount(acc2);
				trans2.setToAccount(acc1);
				trans2.setAmount(transaction.getAmount());
				trans2.setModeOfTransaction(TransactionType.IMPS);
				trans2.setTransactionDate(LocalDateTime.now());
				trans2.setRemarks("Credited for "+transaction.getRemarks()+" from "+acc1.getAccount().getGeneralDetail().getFullName());
				trans2.setMessage(transaction.getMessage());
				
				List<Transaction> t2 = new ArrayList<Transaction>();
				t2.add(trans2);
				
				acc2.setFromTransactions(t2);
				acc1.setToTransactions(t2);
				
				Transaction trans2RefNo = (Transaction)genericRepository.save(trans2);
				
				return "LTIBANK"+trans1RefNo.getTransactionId()+trans2RefNo.getTransactionId();
			}
		}
		catch(NullPointerException e) {
			throw new ServiceException("Invalid account number");
		}
		
	}
}

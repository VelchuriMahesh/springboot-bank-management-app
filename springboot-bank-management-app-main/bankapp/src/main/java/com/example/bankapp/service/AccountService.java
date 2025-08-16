package com.example.bankapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.bankapp.model.Account;
import com.example.bankapp.model.Transaction;
import com.example.bankapp.repository.AccountRepository;
import com.example.bankapp.repository.TransactionRepository;

@Service
public class AccountService implements UserDetailsService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    public Account findAccountByUsername(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found!"));
    }

    public Account registerAccount(String username, String password) {
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setBalance(BigDecimal.ZERO);

        return accountRepository.save(account);
    }

    public void deposit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount,
                "Deposit",
                LocalDateTime.now(),
                account
        );
        transactionRepository.save(transaction);
    }

    public void withdraw(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction transaction = new Transaction(
                amount,
                "Withdrawal",
                LocalDateTime.now(),
                account
        );
        transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionHistory(Account account) {
        return transactionRepository.findByAccountId(account.getId());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = findAccountByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("Username or password not found");
        }

        return new org.springframework.security.core.userdetails.User(
                account.getUsername(),
                account.getPassword(),
                getAuthorities()
        );
    }

    public List<GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("USER"));
    }

    public void transferAmount(Account fromAccount, String toUsername, BigDecimal amount) {
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        Account toAccount = accountRepository.findByUsername(toUsername)
                .orElseThrow(() -> new RuntimeException("Recipient account not found"));

        // Debit from sender
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        accountRepository.save(fromAccount);

        // Credit to receiver
        toAccount.setBalance(toAccount.getBalance().add(amount));
        accountRepository.save(toAccount);

        // Create transactions
        Transaction debitTransaction = new Transaction(
                amount,
                "Transfer To " + toAccount.getUsername(),
                LocalDateTime.now(),
                fromAccount
        );
        transactionRepository.save(debitTransaction);

        Transaction creditTransaction = new Transaction(
                amount,
                "Transfer From " + fromAccount.getUsername(),
                LocalDateTime.now(),
                toAccount
        );
        transactionRepository.save(creditTransaction);
    }
}

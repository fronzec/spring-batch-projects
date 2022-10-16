package com.fronzec.singlethreaded.batchjobs;

import org.springframework.batch.item.ItemProcessor;

import com.fronzec.singlethreaded.batchjobs.persons.Customer;
import com.fronzec.singlethreaded.batchjobs.persons.CustomerOutput;

public class CustomerItemProcessor implements ItemProcessor<Customer, CustomerOutput> {

  @Override
  public CustomerOutput process(Customer customer) throws Exception {
    return CustomerOutput.fromCustomer(customer);
  }
}

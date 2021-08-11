package com.fronzec.myservice.batch;

import org.springframework.batch.item.ItemProcessor;

import com.fronzec.myservice.batch.persons.Customer;
import com.fronzec.myservice.batch.persons.CustomerOutput;

public class CustomerItemProcessor implements ItemProcessor<Customer, CustomerOutput> {

  @Override
  public CustomerOutput process(Customer customer) throws Exception {
    return CustomerOutput.fromCustomer(customer);
  }
}

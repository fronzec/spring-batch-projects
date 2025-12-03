/* (C)2024 */
package com.fronzec.frbatchservice.batchjobs;

import com.fronzec.frbatchservice.batchjobs.persons.Customer;
import com.fronzec.frbatchservice.batchjobs.persons.CustomerOutput;
import org.springframework.batch.infrastructure.item.ItemProcessor;

public class CustomerItemProcessor implements ItemProcessor<Customer, CustomerOutput> {

    @Override
    public CustomerOutput process(Customer customer) throws Exception {
        return CustomerOutput.fromCustomer(customer);
    }
}

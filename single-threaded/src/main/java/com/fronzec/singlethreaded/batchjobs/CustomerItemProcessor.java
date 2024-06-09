/* (C)2024 */
package com.fronzec.singlethreaded.batchjobs;

import com.fronzec.singlethreaded.batchjobs.persons.Customer;
import com.fronzec.singlethreaded.batchjobs.persons.CustomerOutput;
import org.springframework.batch.item.ItemProcessor;

public class CustomerItemProcessor implements ItemProcessor<Customer, CustomerOutput> {

    @Override
    public CustomerOutput process(Customer customer) throws Exception {
        return CustomerOutput.fromCustomer(customer);
    }
}

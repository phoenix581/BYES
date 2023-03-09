package com.byes.paap.rest.uploaddocument.queries;

import com.planonsoftware.platform.backend.querybuilder.v3.IQueryBuilder;
import com.planonsoftware.platform.backend.querybuilder.v3.IQueryDefinition;
import com.planonsoftware.platform.backend.querybuilder.v3.IQueryDefinitionContext;

public class BaseOrderQuery implements IQueryDefinition
{
    @Override
    public void create(IQueryBuilder order, IQueryDefinitionContext aContext) {
         
       order.addSearchField("OrderNumber","orderNumber"); 
       order.addSelectField("Description");  
       order.addSelectField("Syscode");    
    }
   
    @Override
    public String getBOName() {
        return "BaseOrder";
    }
}
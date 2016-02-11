package com.dotmarketing.portlets.rules.model;

import com.dotmarketing.business.FactoryLocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.portlets.rules.exception.RuleEngineException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ConditionGroup implements Serializable, Comparable {
    private static final long serialVersionUID = 1L;
    private String id;
    private String ruleId;
    private Condition.Operator operator;
    private Date modDate;
    private int priority;
    List<Condition> conditions;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Condition.Operator getOperator() {
        return operator;
    }

    public void setOperator(Condition.Operator operator) {
        this.operator = operator;
    }

    public Date getModDate() {
        return modDate;
    }

    public void setModDate(Date modDate) {
        this.modDate = modDate;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<Condition> getConditions() {
        if(conditions == null) {
            try {
                conditions = FactoryLocator.getRulesFactory().getConditionsByGroup(this.id);
            } catch (DotDataException e) {
                throw new RuleEngineException(e, "Could not load conditions for group %s.", this.toString());
            }
        }
        Collections.sort(conditions);
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public void addCondition(Condition condition) {
        if(conditions!=null) {
            conditions.add(condition);
        }
    }

    public void checkValid(){
        for (Condition condition : getConditions()) {
            condition.checkValid();
        }
    }

    public boolean evaluate(HttpServletRequest req, HttpServletResponse res, List<Condition> conditions) {
        boolean result = true;

        /* @todo ggranum: This also fails for ( A AND B OR C)*/
        for (Condition condition : conditions) {
            if(condition.getOperator()== Condition.Operator.AND) {
                result = result && condition.evaluate(req, res);
            } else {
                result = result || condition.evaluate(req, res);
            }
            if(!result) return false;
        }

        return result;
    }

    @Override
	public String toString() {
		return "ConditionGroup [id=" + id + ", ruleId=" + ruleId
				+ ", operator=" + operator + ", modDate=" + modDate
				+ ", priority=" + priority + "]";
	}

    @Override
    public int compareTo(Object o) {
        ConditionGroup c = (ConditionGroup) o;
        return this.priority - c.getPriority();
    }
}

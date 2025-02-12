package com.deepoove.swagger.diff.compare;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSONObject;
import com.deepoove.swagger.diff.model.ChangedParameter;
import com.deepoove.swagger.diff.model.ElProperty;
import com.google.common.collect.Lists;

import io.swagger.models.Model;
import io.swagger.models.parameters.AbstractSerializableParameter;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.StringProperty;

/**
 * compare two parameter
 *
 * @author Sayi
 * @version
 */
public class ParameterDiff {

    private List<Parameter> increased;
    private List<Parameter> missing;
    private List<ChangedParameter> changed;

    Map<String, Model> oldDedinitions;
    Map<String, Model> newDedinitions;

    private ParameterDiff() {
        this.increased = new ArrayList<Parameter>();
        this.missing = new ArrayList<Parameter>();
        this.changed = new ArrayList<ChangedParameter>();
    }

    public static ParameterDiff buildWithDefinition(Map<String, Model> left, Map<String, Model> right) {
        ParameterDiff diff = new ParameterDiff();
        diff.oldDedinitions = left;
        diff.newDedinitions = right;
        return diff;
    }

    public ParameterDiff diff(List<Parameter> left, List<Parameter> right) {
        if (null == left) left = new ArrayList<>();
        if (null == right) right = new ArrayList<>();

        format(left);
        format(right);

        ListDiff<Parameter> paramDiff = ListDiff.diff(left, right, (t, param) -> {
            for (Parameter para : t) {
                if (param.getName().equals(para.getName())) { return para; }
            }
            return null;
        });
        this.increased.addAll(paramDiff.getIncreased());
        this.missing.addAll(paramDiff.getMissing());
        Map<Parameter, Parameter> shared = paramDiff.getShared();
        shared.forEach((leftPara, rightPara) -> {
            ChangedParameter changedParameter = new ChangedParameter();
            changedParameter.setLeftParameter(leftPara);
            changedParameter.setRightParameter(rightPara);
            if (leftPara instanceof BodyParameter && rightPara instanceof BodyParameter) {
                BodyParameter leftBodyPara = (BodyParameter) leftPara;
                Model leftSchema = leftBodyPara.getSchema();
                BodyParameter rightBodyPara = (BodyParameter) rightPara;
                Model rightSchema = rightBodyPara.getSchema();

                ModelDiff diff = ModelDiff.buildWithDefinition(oldDedinitions, newDedinitions).diff(leftSchema,
                        rightSchema, leftPara.getName());
                changedParameter.setIncreased(diff.getIncreased());
                changedParameter.setMissing(diff.getMissing());
                changedParameter.setChanged(diff.getChanged());

            }

            // Let's handle the case where the new API has fx changed the type
            // of PathParameter from being of type String to type integer
            if (leftPara instanceof AbstractSerializableParameter
                    && rightPara instanceof AbstractSerializableParameter) {
                if (!leftPara.equals(rightPara)) {
                    ElProperty elProperty = new ElProperty();
                    elProperty.setEl(rightPara.getName());
                    elProperty.setProperty(mapToProperty(rightPara));
                    changedParameter.setChanged(Lists.newArrayList(elProperty));
                }
            }

            // is requried
            boolean rightRequired = Objects.equals(rightPara.getRequired(), true);
            boolean leftRequired = Objects.equals(leftPara.getRequired(), true);
            changedParameter.setChangeRequired(leftRequired != rightRequired);

            // description
            String description = rightPara.getDescription();
            String oldPescription = leftPara.getDescription();
            if (StringUtils.isBlank(description)) description = "";
            if (StringUtils.isBlank(oldPescription)) oldPescription = "";
            changedParameter.setChangeDescription(!description.equals(oldPescription));

            if (changedParameter.isDiff()) {
                this.changed.add(changedParameter);
            }

        });

        return this;
    }

    private void format(List<Parameter> left) {
        for (Parameter parameter : left) {
            if (Proxy.isProxyClass(parameter.getClass())) {
                InvocationHandler invocationHandler = Proxy.getInvocationHandler(parameter);
                if (invocationHandler instanceof JSONObject) {
                    JSONObject jsonObject = (JSONObject) invocationHandler;
                    jsonObject.put("required", Objects.equals(jsonObject.get("required"), true));
                }
            }
        }
    }

    private Property mapToProperty(Parameter rightPara) {
        Property prop = new StringProperty();
        prop.setAccess(rightPara.getAccess());
        prop.setAllowEmptyValue(rightPara.getAllowEmptyValue());
        prop.setDescription(rightPara.getDescription());
        prop.setName(rightPara.getName());
        prop.setReadOnly(rightPara.isReadOnly());
        prop.setRequired(rightPara.getRequired());
        return prop;
    }

    public List<Parameter> getIncreased() {
        return increased;
    }

    public void setIncreased(List<Parameter> increased) {
        this.increased = increased;
    }

    public List<Parameter> getMissing() {
        return missing;
    }

    public void setMissing(List<Parameter> missing) {
        this.missing = missing;
    }

    public List<ChangedParameter> getChanged() {
        return changed;
    }

    public void setChanged(List<ChangedParameter> changed) {
        this.changed = changed;
    }

}

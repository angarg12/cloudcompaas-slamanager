/*******************************************************************************
 * Copyright (c) 2013, Andrés García García All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * (2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * (3) Neither the name of the Universitat Politècnica de València nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.cloudcompaas.sla.wsag;

/* 
 * Copyright (c) 2007, Fraunhofer-Gesellschaft
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the disclaimer at the end.
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 * 
 * (2) Neither the name of Fraunhofer nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 */
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import javax.xml.namespace.QName;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeLoader;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.impl.xb.xsdschema.ComplexRestrictionType;
import org.apache.xmlbeans.impl.xb.xsdschema.ComplexType;
import org.apache.xmlbeans.impl.xb.xsdschema.FormChoice;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument.Schema;
import org.apache.xmlbeans.impl.xb.xsdschema.SimpleType;
import org.ogf.graap.wsag4j.types.configuration.SchemaImportType;
import org.ogf.graap.wsag4j.types.configuration.ValidatorType;
import org.ogf.graap.wsag4j.types.engine.ConstraintAnnotationDocument;
import org.ogf.graap.wsag4j.types.engine.ConstraintAnnotationType;
import org.ogf.graap.wsag4j.types.engine.ItemCardinalityType;
import org.ogf.schemas.graap.wsAgreement.OfferItemType;
import org.ogf.schemas.graap.wsAgreement.OfferItemType.ItemConstraint;

/**
 * The {@link FragmentValidator} implements the required methods to validate the
 * compliance of an agreement offer with respect to the creation constraints
 * that are defined in the template that was used to create the offer.
 * 
 * @author Oliver Waeldrich
 * 
 */
public class FragmentValidator {

	private static final String GENERATED_TYPE_NAME = "GeneratedConstraintValidationType";

	private static final String XML_SCHEMA_FILENAME = "/validator/XMLSchema.xml";

	private static final String WSAG_SCHEMA_FILENAME = "/validator/ws-agreement-xsd-types.xsd";

	private static final String CCPAAS_SCHEMA_FILENAME = "/validator/ccpaas.xsd-1.xsd";

	private final HashMap<String, Boolean[]> knownSchemaFormChoice = new HashMap<String, Boolean[]>();

	private ValidatorType configuration;

	//
	// type system loader
	//
	private SchemaTypeLoader loader;

	/**
     * 
     */
	public FragmentValidator() {
		//
		// initialize the validator configuration
		//
		configuration = ValidatorType.Factory.newInstance();
		configuration.addNewSchemaImports();
		configuration.getSchemaImports().addNewSchemaFilename()
				.setStringValue(XML_SCHEMA_FILENAME);
		configuration.getSchemaImports().addNewSchemaFilename()
				.setStringValue(WSAG_SCHEMA_FILENAME);
		configuration.getSchemaImports().addNewSchemaFilename()
				.setStringValue(CCPAAS_SCHEMA_FILENAME);
	}

	public boolean validate(String fragment) {
		try {
			XmlObject object = XmlObject.Factory.parse(fragment);

			boolean validFragment = validate(object);

			if (!validFragment) {
				return false;
			}

			XmlObject[] constraintItems = object
					.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';"
							+ "//ws:CreationConstraints/ws:Item");
			OfferItemType.Factory.newInstance();

			OfferItemType[] items = new OfferItemType[constraintItems.length];

			for (int i = 0; i < items.length; i++) {
				items[i] = OfferItemType.Factory.parse(constraintItems[i]
						.xmlText());
			}

			for (int i = 0; i < items.length; i++) {
				if (!validateConstraint(object, items[i])) {
					return false;
				}
			}

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Returns the validator configuration.
	 * 
	 * @return the validator configuration
	 */
	public ValidatorType getConfiguration() {
		return configuration;
	}

	/**
	 * Sets the validator configuration. The configuration contains a set of XML
	 * schemas that are used to validate the XML documents (template, offer,
	 * couonter offer).
	 * 
	 * @param configuration
	 *            the validator configuration
	 */
	public void setConfiguration(ValidatorType configuration) {
		this.configuration = configuration;
	}

	private boolean validateConstraint(XmlObject target, OfferItemType item) {
		try {
			boolean result = true;

			XmlObject[] items = target.selectPath(item.getLocation());

			//
			// Check whether a cardinality of the expected selection result is
			// specified
			// an whether the result fits to the specified selection constraints
			//
			result = checkItemCardinality(item, items);
			if (!result) {
				return result;
			}

			//
			// create schema for type validation
			//
			HashMap<SchemaType, SchemaTypeLoader> schemaLoaderMap = new HashMap<SchemaType, SchemaTypeLoader>();
			HashMap<SchemaType, SchemaType> schemaTypeMap = new HashMap<SchemaType, SchemaType>();

			for (int i = 0; i < items.length; i++) {

				SchemaType sourcetype = getSourceType(items[i]);

				if (!schemaTypeMap.containsKey(sourcetype)) {

					SchemaDocument schema = initializeSchema(sourcetype);

					Schema generatedSchema = createSchemaType(
							schema.getSchema(), sourcetype,
							item.getItemConstraint());

					try {
						QName generatedTypeQName = new QName(sourcetype
								.getName().getNamespaceURI(),
								GENERATED_TYPE_NAME, "wsag4j");
						SchemaTypeLoader schemaLoader = getLoader(generatedSchema);
						SchemaType schemaType = schemaLoader
								.findType(generatedTypeQName);
						schemaTypeMap.put(sourcetype, schemaType);
						schemaLoaderMap.put(sourcetype, schemaLoader);
					} catch (Exception e) {

						return false;
					}
				}
			}

			for (int i = 0; i < items.length; i++) {

				XmlOptions serializeOptions = new XmlOptions(new XmlOptions());
				serializeOptions.setSaveOuter();

				String serializedItem = items[i].xmlText(serializeOptions);

				try {
					SchemaType sourceType = getSourceType(items[i]);

					SchemaType restrictionType = schemaTypeMap.get(sourceType);

					XmlOptions schemaOptions = new XmlOptions(new XmlOptions());
					schemaOptions.setLoadReplaceDocumentElement(null);
					schemaOptions.setDocumentType(restrictionType);

					//
					// TODO: For better performance, parsing templates without
					// line numbers
					// could be done using the getDom() method.
					//
					SchemaTypeLoader schemaLoader = schemaLoaderMap
							.get(sourceType);

					XmlObject check = schemaLoader.parse(serializedItem,
							restrictionType, schemaOptions);
					result = result && validate(check);

				} catch (Exception e) {
					result = false;
					break;
				}
			}

			return result;
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * @param sourcetype
	 * @return
	 */
	private SchemaDocument initializeSchema(SchemaType sourcetype) {
		SchemaDocument schema = SchemaDocument.Factory.newInstance();
		schema.addNewSchema();

		//
		// FIXME: add support for anonymous type validation
		//
		// TODO: sourcetype.getName().getNamespaceURI() does not work for
		// anonymous
		// type declarations, such as:
		//
		// <element>
		// <complexType>
		// </complexType>
		// </element>
		//
		schema.getSchema().setTargetNamespace(
				sourcetype.getName().getNamespaceURI());
		schema.getSchema().setVersion("1.0");

		schema.getSchema().setElementFormDefault(FormChoice.UNQUALIFIED);
		schema.getSchema().setAttributeFormDefault(FormChoice.UNQUALIFIED);

		String sourceNamespace = sourcetype.getName().getNamespaceURI();
		if (knownSchemaFormChoice.containsKey(sourceNamespace)) {
			Boolean[] efq = knownSchemaFormChoice.get(sourceNamespace);
			boolean isElementQualified = efq[0].booleanValue();
			boolean isAttributeQualified = efq[1].booleanValue();

			if (isElementQualified) {
				schema.getSchema().setElementFormDefault(FormChoice.QUALIFIED);
			}
			if (isAttributeQualified) {
				schema.getSchema()
						.setAttributeFormDefault(FormChoice.QUALIFIED);
			}
		}
		return schema;
	}

	/**
	 * @param item
	 * @param items
	 * @param annotation
	 */
	private boolean checkItemCardinality(OfferItemType item, XmlObject[] items) {

		ConstraintAnnotationType annotation = ConstraintAnnotationType.Factory
				.newInstance();
		annotation.setMultiplicity(ItemCardinalityType.X_0_TO_N);
		XmlObject[] cadinalityDoc = item
				.selectChildren(ConstraintAnnotationDocument.type
						.getDocumentElementName());
		if (cadinalityDoc.length > 0) {
			annotation = (ConstraintAnnotationType) cadinalityDoc[0];
		}

		switch (annotation.getMultiplicity().intValue()) {
		case ItemCardinalityType.INT_X_0_TO_1:

			if (items.length > 1) {

				return false;
			}
			return true;

		case ItemCardinalityType.INT_X_1:

			if (items.length != 1) {
				return false;
			}
			return true;

		case ItemCardinalityType.INT_X_1_TO_N:

			if (items.length == 0) {
				return false;
			}
			return true;

		default:
			//
			// is satisfied anyway
			//
			return true;
		}
	}

	private boolean validate(XmlObject object) {
		ArrayList<XmlError> list = new ArrayList<XmlError>();

		XmlOptions voptions = new XmlOptions(new XmlOptions());
		voptions.setErrorListener(list);

		if (!object.validate(voptions)) {
			Iterator<XmlError> it = list.iterator();
			while(it.hasNext()){
				it.next();
			}
			return false;
		}

		return true;
	}

	private SchemaType getSourceType(XmlObject item) throws Exception {
		SchemaType sourcetype = item.schemaType().getPrimitiveType();

		if (sourcetype == null) {
			sourcetype = item.schemaType();

			if (sourcetype.isNoType()) {
				throw new Exception("No type information found for item: "
						+ item.xmlText());
			}
		}

		return sourcetype;
	}

	private Schema createSchemaType(Schema schema, SchemaType type,
			ItemConstraint constraint) {

		Schema result = null;

		// We first check the type of the constraint. We can have a
		// typeDefParticle or a simpleRestrictionModel constraint
		if (constraint.isSetAll() || constraint.isSetChoice()
				|| constraint.isSetGroup() || constraint.isSetSequence()) {

			result = createTypeDefParticleSchema(schema, type, constraint);
		} else {
			result = createSimpleRestrictionModelSchema(schema, type,
					constraint);
		}

		return result;
	}

	private Schema createSimpleRestrictionModelSchema(Schema schema,
			SchemaType type, ItemConstraint constraint) {
		QName typeName = new QName(
				"http://wsag4j.scai.fraunhofer.de/generated",
				"GeneratedConstraintValidationType", "wsag4j");

		SimpleType simple = schema.addNewSimpleType();
		simple.setName(typeName.getLocalPart());
		simple.addNewRestriction();

		if (constraint.isSetSimpleType()) {
			simple.getRestriction().setSimpleType(constraint.getSimpleType());
		} else {
			simple.getRestriction().setBase(type.getPrimitiveType().getName());
		}

		simple.getRestriction().setLengthArray(constraint.getLengthArray());

		simple.getRestriction().setMinInclusiveArray(
				constraint.getMinInclusiveArray());
		simple.getRestriction().setMaxInclusiveArray(
				constraint.getMaxInclusiveArray());

		simple.getRestriction().setMinExclusiveArray(
				constraint.getMinExclusiveArray());
		simple.getRestriction().setMaxExclusiveArray(
				constraint.getMaxExclusiveArray());

		simple.getRestriction().setEnumerationArray(
				constraint.getEnumerationArray());

		simple.getRestriction().setLengthArray(constraint.getLengthArray());
		simple.getRestriction().setMaxLengthArray(
				constraint.getMaxLengthArray());
		simple.getRestriction().setMinLengthArray(
				constraint.getMinLengthArray());

		simple.getRestriction().setFractionDigitsArray(
				constraint.getFractionDigitsArray());

		simple.getRestriction().setPatternArray(constraint.getPatternArray());
		simple.getRestriction().setTotalDigitsArray(
				constraint.getTotalDigitsArray());
		simple.getRestriction().setWhiteSpaceArray(
				constraint.getWhiteSpaceArray());

		return schema;
	}

	private Schema createTypeDefParticleSchema(Schema schema, SchemaType type,
			ItemConstraint constraint) {
		ComplexType complex = schema.addNewComplexType();
		complex.setName(GENERATED_TYPE_NAME);

		ComplexRestrictionType restriction = complex.addNewComplexContent()
				.addNewRestriction();
		restriction.setBase(type.getName());

		if (constraint.isSetAll()) {
			restriction.setAll(constraint.getAll());
		}
		if (constraint.isSetChoice()) {
			restriction.setChoice(constraint.getChoice());
		}
		if (constraint.isSetSequence()) {
			restriction.setSequence(constraint.getSequence());
		}
		if (constraint.isSetGroup()) {
			restriction.setGroup(constraint.getGroup());
		}

		return schema;
	}

	/**
	 * Creates a type system loader based on the validator configuration. This
	 * loader is completely compiled from XML schema files specified in the
	 * validator configuration.
	 * 
	 * @param schema
	 *            an additional schema file to include in the type system loader
	 * @return union of the validator type system loader and the loader of the
	 *         provided schema file
	 * @throws Exception
	 */
	private synchronized SchemaTypeLoader getLoader(Schema schema)
			throws Exception {

		//
		// First, we get the WSAG loader. This loader contains all global type
		// systems
		// for the WSAG4J engine.
		//
		SchemaTypeLoader wsagLoader = getLoader();

		//
		// now we parse and compile the schema while using the global type
		// system
		//
		SchemaDocument importSchema = SchemaDocument.Factory.parse(schema
				.getDomNode());
		SchemaTypeSystem schemats = XmlBeans.compileXsd(
				new XmlObject[] { importSchema }, wsagLoader, new XmlOptions());

		//
		// the last step is to do a type loader union of our local schema and
		// our global type systems
		//
		return XmlBeans.typeLoaderUnion(new SchemaTypeLoader[] { schemats,
				wsagLoader });
	}

	/**
	 * Creates a type system loader based on the validator configuration. This
	 * loader is completely compiled from XML schema files specified in the
	 * validator configuration.
	 * 
	 * @return the validator type system loader
	 * @throws Exception
	 */
	private synchronized SchemaTypeLoader getLoader() throws Exception {
		//
		// If the WSAG4J Loader is not initialized, do the initialization
		// based on the validator configuration.
		//
		if (loader == null) {

			// Remove for now, this would introduce system specific dependencies
			// XmlOptions parserOptions = new XmlOptions();
			// parserOptions.setLoadUseXMLReader(
			// SAXParserFactory.newInstance().newSAXParser().getXMLReader()
			// );

			Vector<SchemaTypeSystem> wsag4jTypeSystems = new Vector<SchemaTypeSystem>();

			//
			// add the build in type system as initial type system
			//
			wsag4jTypeSystems.add(XmlBeans.getBuiltinTypeSystem());

			//
			// for each explicitly referenced schema, create a new type system
			// and add the type system as a wsag4j type system
			//
			SchemaImportType imports = getConfiguration().getSchemaImports();
			if (imports != null) {
				String[] schemaFilenames = imports.getSchemaFilenameArray();
				for (int i = 0; i < schemaFilenames.length; i++) {
					try {
						InputStream resource = FragmentValidator.class
								.getResourceAsStream(schemaFilenames[i]);
						SchemaDocument importSchema = SchemaDocument.Factory
								.parse(resource);

						if (!knownSchemaFormChoice.containsKey(importSchema
								.getSchema().getTargetNamespace())) {
							boolean isAttributeQualified = false;
							boolean isElementQualified = false;

							if (importSchema.getSchema()
									.isSetAttributeFormDefault()) {
								isAttributeQualified = importSchema.getSchema()
										.getAttributeFormDefault() == FormChoice.QUALIFIED;
							}

							if (importSchema.getSchema()
									.isSetElementFormDefault()) {
								isElementQualified = importSchema.getSchema()
										.getElementFormDefault() == FormChoice.QUALIFIED;
							}

							knownSchemaFormChoice.put(importSchema.getSchema()
									.getTargetNamespace(), new Boolean[] {
									Boolean.valueOf(isElementQualified),
									Boolean.valueOf(isAttributeQualified) });
						}

						SchemaTypeSystem schemats = XmlBeans.compileXsd(
								new XmlObject[] { importSchema }, loader,
								new XmlOptions());
						wsag4jTypeSystems.add(schemats);

					} catch (Exception e) {
					}
				}
			}

			SchemaTypeLoader[] typeSystem = wsag4jTypeSystems
					.toArray(new SchemaTypeLoader[wsag4jTypeSystems.size()]);

			loader = XmlBeans.typeLoaderUnion(typeSystem);
		}

		return loader;
	}
}

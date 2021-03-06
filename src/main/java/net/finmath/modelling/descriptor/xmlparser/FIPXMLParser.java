package net.finmath.modelling.descriptor.xmlparser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import net.finmath.modelling.ProductDescriptor;
import net.finmath.modelling.descriptor.InterestRateSwapLegProductDescriptor;
import net.finmath.modelling.descriptor.InterestRateSwapProductDescriptor;
import net.finmath.modelling.descriptor.ScheduleDescriptor;
import net.finmath.time.Period;
import net.finmath.time.daycount.DayCountConventionFactory;
import net.finmath.time.daycount.DayCountConventionInterface;

/**
 * Class for parsing trades saved in FIPXML to product descriptors.
 * 
 * @author Christian Fries
 * @author Roland Bachl
 *
 */
public class FIPXMLParser implements XMLParser {

	private final boolean agentIsBuyer;
	private final String discountCurveName;

	/**
	 * Construct the parser with default parameters. I.e. agent is buyer. Name of discount curve will be taken from file.
	 */
	public FIPXMLParser() {
		this(false, null);
	}

	/**
	 * Construct the parser.
	 * 
	 * @param agentIsBuyer Boolean indicating whether valuation is done from the buyers perspective.
	 * @param discountCurveName Name of the discount curve to be assigned to the descriptor. If set to null or left blank the parser will try to determine this from the file.
	 */
	public FIPXMLParser(boolean agentIsBuyer, String discountCurveName) {
		super();
		this.agentIsBuyer = agentIsBuyer;
		this.discountCurveName = discountCurveName;
	}

	@Override
	public ProductDescriptor getProductDescriptor(File file) throws SAXException, IOException, ParserConfigurationException {

		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
		doc.getDocumentElement().normalize();

		//Check compatibility and assign proper parser
		if(! doc.getDocumentElement().getNodeName().equalsIgnoreCase("FIPXML")) {
			throw new IllegalArgumentException("This parser is meant for XML of type FIPXML, but file was "+doc.getDocumentElement().getNodeName()+".");
		}

		if(doc.getElementsByTagName("instrumentName").item(0).getTextContent().equalsIgnoreCase("Interest Rate Swap")) {
			return getSwapProductDescriptor(file);
		} else {
			throw new IllegalArgumentException("This xml parser is not set up to process trade of type "+doc.getElementsByTagName("instrumentName").item(0).getTextContent());
		}
	}

	/**
	 * Parse a product descriptor from a file containing a swap trade.
	 *
	 * @param file File containing a swap trade.
	 * @return Product descriptor extracted from the file.
	 * @throws SAXException
	 * @throws IOException
	 * @throws ParserConfigurationException
	 */
	public InterestRateSwapProductDescriptor getSwapProductDescriptor(File file) throws SAXException, IOException, ParserConfigurationException {

		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
		doc.getDocumentElement().normalize();

		//Check compatibility
		if(! doc.getDocumentElement().getNodeName().equalsIgnoreCase("FIPXML")) {
			throw new IllegalArgumentException("This parser is meant for XML of type FIPXML, but file was "+doc.getDocumentElement().getNodeName()+".");
		}

		if(doc.getElementsByTagName("instrumentName").item(0).getTextContent().equalsIgnoreCase("Interest Rate Swap")) {
			if (doc.getElementsByTagName("legAgreement").getLength() != 2) {
				throw new IllegalArgumentException("Unknown swap configuration. Number of swap legs was "+doc.getElementsByTagName("legAgreement").getLength());
			}
		} else {
			throw new IllegalArgumentException("This xml parser is not set up to process trade of type "+doc.getElementsByTagName("instrumentName").item(0).getTextContent());
		}

		DayCountConventionInterface daycountConvention = DayCountConventionFactory.getDayCountConvention(doc.getElementsByTagName("dayCountFraction").item(0).getTextContent());

		//TODO try to get curves from file. Problems if there are two float/fixed legs
		//forward curve
		String forwardCurveName = null;
		NodeList temp = doc.getElementsByTagName("instrumentId");
		for(int index = 0; index < temp.getLength(); index++) {
			Node id = temp.item(index);
			if(id.getAttributes().getNamedItem("instrumentIdScheme").getTextContent().equalsIgnoreCase("INTERESTRATE")) {
				forwardCurveName = id.getTextContent();
				break;
			}
		}

		//Discount curve
		String[] split = forwardCurveName.split("_");
		String discountCurveName = (this.discountCurveName == null || this.discountCurveName.length() == 0 ) ? split[0] +"_"+split[1] : this.discountCurveName;

		InterestRateSwapLegProductDescriptor legReceiver = null;
		InterestRateSwapLegProductDescriptor legPayer = null;

		//Get descriptors for both legs
		NodeList legs = doc.getElementsByTagName("legAgreement");
		for(int legIndex = 0; legIndex < legs.getLength(); legIndex++) {
			Element leg = (Element) legs.item(legIndex);

			boolean isPayer = (leg.getElementsByTagName("payDirection").item(0).getTextContent().equalsIgnoreCase("SELLER_TO_BUYER") && !agentIsBuyer)
					|| (leg.getElementsByTagName("payDirection").item(0).getTextContent().equalsIgnoreCase("BUYER_TO_SELLER") && agentIsBuyer);
			boolean isFixed = leg.getElementsByTagName("interestType").item(0).getTextContent().equals("FIX");

			if(isPayer) {
				legPayer = getSwapLegProductDescriptor(leg, isFixed ? null : forwardCurveName, discountCurveName, daycountConvention);
			} else {
				legReceiver = getSwapLegProductDescriptor(leg, isFixed ? null : forwardCurveName, discountCurveName, daycountConvention);
			}

		}

		return new InterestRateSwapProductDescriptor(legReceiver, legPayer);

	}

	/**
	 * Construct an InterestRateSwapLegProductDescriptor from a node in a FIPXML file.
	 * 
	 * @param leg The node containing the leg.
	 * @param forwardCurveName Forward curve name form outside the node.
	 * @param discountCurveName Discount curve name form outside the node.
	 * @param daycountConvention Daycount convention from outside the node.
	 * @return Descriptor of the swap leg.
	 */
	private static InterestRateSwapLegProductDescriptor getSwapLegProductDescriptor(Element leg, String forwardCurveName, String discountCurveName,
			DayCountConventionInterface daycountConvention) {

		boolean isFixed = leg.getElementsByTagName("interestType").item(0).getTextContent().equalsIgnoreCase("FIX");

		ArrayList<Period> periods 		= new ArrayList<>();
		ArrayList<Double> notionalsList	= new ArrayList<>();
		ArrayList<Double> rates			= new ArrayList<>();

		//extracting data for each period
		NodeList periodsXML = leg.getElementsByTagName("incomePayment");
		for(int periodIndex = 0; periodIndex < periodsXML.getLength(); periodIndex++) {

			Element periodXML = (Element) periodsXML.item(periodIndex);

			LocalDate startDate	= LocalDate.parse(periodXML.getElementsByTagName("startDate").item(0).getTextContent());
			LocalDate endDate	= LocalDate.parse(periodXML.getElementsByTagName("endDate").item(0).getTextContent());

			LocalDate fixingDate	= startDate;
			LocalDate paymentDate	= LocalDate.parse(periodXML.getElementsByTagName("payDate").item(0).getTextContent());

			if(! isFixed) {
				fixingDate = LocalDate.parse(periodXML.getElementsByTagName("fixingDate").item(0).getTextContent());
			}

			periods.add(new Period(fixingDate, paymentDate, startDate, endDate));

			double notional		= Double.parseDouble(periodXML.getElementsByTagName("nominal").item(0).getTextContent());
			notionalsList.add(new Double(notional));

			if(isFixed) {
				double fixedRate	= Double.parseDouble(periodXML.getElementsByTagName("fixedRate").item(0).getTextContent());
				rates.add(new Double(fixedRate));
			} else {
				rates.add(new Double(0));
			}

		}

		ScheduleDescriptor schedule = new ScheduleDescriptor(periods, daycountConvention);
		double[] notionals	= notionalsList.stream().mapToDouble(Double::doubleValue).toArray();
		double[] spreads	= rates.stream().mapToDouble(Double::doubleValue).toArray();

		return new InterestRateSwapLegProductDescriptor(forwardCurveName, discountCurveName, schedule, notionals, spreads, false);
	}

}

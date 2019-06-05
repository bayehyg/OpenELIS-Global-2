package spring.mine.patient.controller;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.apache.commons.beanutils.PropertyUtils;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import spring.mine.patient.form.PatientEntryByProjectForm;
import spring.mine.patient.validator.PatientEntryByProjectFormValidator;
import spring.util.SpringContext;
import us.mn.state.health.lims.common.util.DateUtil;
import us.mn.state.health.lims.patient.saving.Accessioner;
import us.mn.state.health.lims.patient.saving.PatientEditUpdate;
import us.mn.state.health.lims.patient.saving.PatientEntry;
import us.mn.state.health.lims.patient.saving.PatientEntryAfterAnalyzer;
import us.mn.state.health.lims.patient.saving.PatientEntryAfterSampleEntry;
import us.mn.state.health.lims.patient.saving.PatientSecondEntry;
import us.mn.state.health.lims.patient.saving.RequestType;

@Controller
public class PatientEntryByProjectController extends BasePatientEntryByProject {

	@Autowired
	PatientEntryByProjectFormValidator formValidator;

	@RequestMapping(value = "/PatientEntryByProject", method = RequestMethod.GET)
	public ModelAndView showPatientEntryByProject(HttpServletRequest request)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

		PatientEntryByProjectForm form = new PatientEntryByProjectForm();

		String todayAsText = DateUtil.formatDateAsText(new Date());

		request.getSession().setAttribute(SAVE_DISABLED, TRUE);

		// retrieve the current project, before clearing, so that we can set it on
		// later.
		String projectFormName = Accessioner.findProjectFormName(form);
		updateRequestType(request);

		addAllPatientFormLists(form);

		PropertyUtils.setProperty(form, "currentDate", todayAsText);
		PropertyUtils.setProperty(form, "receivedDateForDisplay", todayAsText);
		PropertyUtils.setProperty(form, "interviewDate", todayAsText);
		// put the projectFormName back in.
		setProjectFormName(form, projectFormName);

		addFlashMsgsToRequest(request);

		return findForward(FWD_SUCCESS, form);
	}

	// TODO consider making separate method for each type of form entry so can use
	// @Validated(VL.class, EID.class, etc..) to access seperate logic
	@RequestMapping(value = "/PatientEntryByProject", method = RequestMethod.POST)
	public ModelAndView showPatientEntryByProjectUpdate(HttpServletRequest request,
			@ModelAttribute("form") @Valid PatientEntryByProjectForm form, BindingResult result,
			RedirectAttributes redirectAttributes) throws Exception {
		formValidator.validate(form, result);
		if (result.hasErrors()) {
			saveErrors(result);
			return findForward(FWD_FAIL_INSERT, form);
		}

		String sysUserId = getSysUserId(request);
		addAllPatientFormLists(form);
		PatientEditUpdate patientEditUpdateAccessioner = SpringContext.getBean(PatientEditUpdate.class);
		patientEditUpdateAccessioner.setRequest(request);
		patientEditUpdateAccessioner.setFieldsFromForm(form);
		patientEditUpdateAccessioner.setSysUserId(sysUserId);
		String forward = FWD_FAIL_INSERT;
		if (patientEditUpdateAccessioner.canAccession()) {
			forward = handleSave(request, patientEditUpdateAccessioner);
		}

		PatientSecondEntry patientSecondEntryAccessioner = SpringContext.getBean(PatientSecondEntry.class);
		patientSecondEntryAccessioner.setRequest(request);
		patientSecondEntryAccessioner.setFieldsFromForm(form);
		patientSecondEntryAccessioner.setSysUserId(sysUserId);
		if (patientSecondEntryAccessioner.canAccession()) {
			forward = handleSave(request, patientSecondEntryAccessioner);
		}
		PatientEntry patientEntryAccessioner = SpringContext.getBean("patientEntry");
		patientEntryAccessioner.setRequest(request);
		patientEntryAccessioner.setFieldsFromForm(form);
		patientEntryAccessioner.setSysUserId(sysUserId);
		if (patientEntryAccessioner.canAccession()) {
			forward = handleSave(request, patientEntryAccessioner);
		}
		PatientEntryAfterSampleEntry patientEntryAfterSampleEntryAccessioner = SpringContext
				.getBean(PatientEntryAfterSampleEntry.class);
		patientEntryAfterSampleEntryAccessioner.setRequest(request);
		patientEntryAfterSampleEntryAccessioner.setFieldsFromForm(form);
		patientEntryAfterSampleEntryAccessioner.setSysUserId(sysUserId);
		if (patientEntryAfterSampleEntryAccessioner.canAccession()) {
			forward = handleSave(request, patientEntryAfterSampleEntryAccessioner);
		}
		PatientEntryAfterAnalyzer patientEntryAfterAnalyzerAccessioner = SpringContext
				.getBean(PatientEntryAfterAnalyzer.class);
		patientEntryAfterAnalyzerAccessioner.setRequest(request);
		patientEntryAfterAnalyzerAccessioner.setFieldsFromForm(form);
		patientEntryAfterAnalyzerAccessioner.setSysUserId(sysUserId);
		if (patientEntryAfterAnalyzerAccessioner.canAccession()) {
			forward = handleSave(request, patientEntryAfterAnalyzerAccessioner);
		}
		if (FWD_FAIL_INSERT.equals(forward) || forward == null) {
			logAndAddMessage(request, "performAction", "errors.UpdateException");
			forward = FWD_FAIL_INSERT;
		} else if (FWD_SUCCESS_INSERT.equals(forward)) {
			redirectAttributes.addFlashAttribute(FWD_SUCCESS, true);
		}

		return findForward(forward, form);
	}

	@Override
	protected String findLocalForward(String forward) {
		if (FWD_SUCCESS.equals(forward)) {
			return "patientEntryByProjectDefinition";
		} else if (FWD_FAIL.equals(forward)) {
			return "homePageDefinition";
		} else if (FWD_SUCCESS_INSERT.equals(forward)) {
			String redirectURL = "/PatientEntryByProject.do?type="
					+ Encode.forUriComponent(request.getParameter("type"));
			return "redirect:" + redirectURL;
		} else if (FWD_FAIL_INSERT.equals(forward)) {
			return "patientEntryByProjectDefinition";
		} else {
			return "PageNotFound";
		}
	}

	@Override
	protected String getPageTitleKey() {
		return "patient.project.title";
	}

	@Override
	protected String getPageSubtitleKey() {
		RequestType requestType = getRequestType(request);
		String key = null;
		switch (requestType) {
		case INITIAL: {
			key = "banner.menu.createPatient.Initial";
			break;
		}
		case VERIFY: {
			key = "banner.menu.createPatient.Verify";
			break;
		}

		default: {
			key = "banner.menu.createPatient.Initial";
		}
		}

		return key;
	}
}
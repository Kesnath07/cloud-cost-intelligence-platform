package com.cloudcost.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cloudcost.analysis.CostAnalysisService;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/budgets")
class BudgetController {

    private final BudgetService budgets;
    private final CostAnalysisService analysis;

    BudgetController(BudgetService budgets, CostAnalysisService analysis) {
        this.budgets = budgets;
        this.analysis = analysis;
    }

    @GetMapping
    String list(@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate asOf, Model model) {
        LocalDate date = asOf != null ? asOf : budgets.defaultAsOf();
        List<CostBudget> all = budgets.list();
        Map<Long, BudgetEvaluation> evaluations = all.stream()
                .collect(Collectors.toMap(CostBudget::getId, budget -> budgets.evaluate(budget, date)));
        model.addAttribute("budgets", all);
        model.addAttribute("evaluations", evaluations);
        model.addAttribute("asOf", date);
        model.addAttribute("defaultAsOf", budgets.defaultAsOf());
        return "budgets/list";
    }

    @GetMapping("/new")
    String newBudget(Model model) {
        return form(model, new BudgetForm(), null);
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable long id, Model model) {
        return form(model, BudgetForm.from(budgets.get(id)), id);
    }

    @PostMapping
    String create(@Valid @ModelAttribute("budget") BudgetForm form, BindingResult result, Model model,
            RedirectAttributes redirect) {
        return save(form, result, model, redirect, null, budgets::create);
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @Valid @ModelAttribute("budget") BudgetForm form, BindingResult result,
            Model model, RedirectAttributes redirect) {
        return save(form, result, model, redirect, id, valid -> budgets.update(id, valid));
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        String name = budgets.get(id).getName();
        budgets.delete(id);
        redirect.addFlashAttribute("notice", "Deleted budget '" + name + "'.");
        return "redirect:/budgets";
    }

    private String save(BudgetForm form, BindingResult result, Model model, RedirectAttributes redirect, Long id,
            Function<BudgetForm, CostBudget> action) {
        if (!result.hasErrors()) {
            try {
                CostBudget saved = action.apply(form);
                redirect.addFlashAttribute("notice", "Saved budget '" + saved.getName() + "'.");
                return "redirect:/budgets";
            } catch (DuplicateBudgetNameException ex) {
                result.rejectValue("name", "duplicate", ex.getMessage());
            }
        }
        return form(model, form, id);
    }

    private String form(Model model, BudgetForm form, Long id) {
        model.addAttribute("budget", form);
        model.addAttribute("budgetId", id);
        model.addAttribute("periods", BudgetPeriod.values());
        model.addAttribute("services", analysis.availableServices());
        model.addAttribute("environments", analysis.availableEnvironments());
        return "budgets/form";
    }
}

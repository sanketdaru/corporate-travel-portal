package com.corporate.travel.expense.testutil;

import com.corporate.travel.expense.model.entity.Expense;
import com.corporate.travel.expense.model.entity.ExpenseItem;
import com.corporate.travel.models.ExpenseCategory;
import com.corporate.travel.models.ExpenseStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static factory methods for common expense test scenarios
 * Provides pre-configured test fixtures for typical use cases
 */
public class ExpenseTestFixtures {
    
    // Test user IDs
    public static final String ALICE_USER_ID = "alice.employee";
    public static final String BOB_USER_ID = "bob.manager";
    public static final String CAROL_USER_ID = "carol.executive";
    public static final String DAVE_USER_ID = "dave.assistant";
    public static final String EVE_USER_ID = "eve.employee";
    
    // Test tenant IDs
    public static final String TENANT_A = "tenant-a";
    public static final String TENANT_B = "tenant-b";
    
    // Test expense IDs
    public static final UUID EXPENSE_ID_1 = UUID.fromString("e1111111-1111-1111-1111-111111111111");
    public static final UUID EXPENSE_ID_2 = UUID.fromString("e2222222-2222-2222-2222-222222222222");
    public static final UUID EXPENSE_ID_3 = UUID.fromString("e3333333-3333-3333-3333-333333333333");
    
    // Test expense item IDs
    public static final UUID ITEM_ID_1 = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    public static final UUID ITEM_ID_2 = UUID.fromString("a2222222-2222-2222-2222-222222222222");
    public static final UUID ITEM_ID_3 = UUID.fromString("a3333333-3333-3333-3333-333333333333");
    
    // ========== Expense Fixtures ==========
    
    /**
     * Create a draft expense for Alice with no items
     */
    public static Expense draftExpenseForAlice() {
        return ExpenseTestDataBuilder.anExpense()
                .withId(EXPENSE_ID_1)
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .inDraftStatus()
                .withNoItems()
                .build();
    }
    
    /**
     * Create an empty draft expense for Alice (alias for clarity)
     */
    public static Expense emptyDraftExpenseForAlice() {
        return draftExpenseForAlice();
    }
    
    /**
     * Create a draft expense for Alice with multiple items
     */
    public static Expense draftExpenseWithItemsForAlice() {
        return ExpenseTestDataBuilder.anExpense()
                .withId(EXPENSE_ID_1)
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .inDraftStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create a submitted expense for Bob
     */
    public static Expense submittedExpenseForBob() {
        return ExpenseTestDataBuilder.anExpense()
                .withId(EXPENSE_ID_2)
                .withTenantId(TENANT_A)
                .withUserId(BOB_USER_ID)
                .withCreatedBy(BOB_USER_ID)
                .withUpdatedBy(BOB_USER_ID)
                .inSubmittedStatus()
                .withTravelExpenseItems()
                .build();
    }
    
    /**
     * Create an approved expense for Carol
     */
    public static Expense approvedExpenseForCarol() {
        return ExpenseTestDataBuilder.anExpense()
                .withId(EXPENSE_ID_3)
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)
                .withCreatedBy(CAROL_USER_ID)
                .withUpdatedBy(CAROL_USER_ID)
                .inApprovedStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create a rejected expense for Alice
     */
    public static Expense rejectedExpenseForAlice() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .inRejectedStatus()
                .withMultipleItems(2)
                .build();
    }
    
    /**
     * Create a paid expense for Carol
     */
    public static Expense paidExpenseForCarol() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)
                .withCreatedBy(CAROL_USER_ID)
                .withUpdatedBy(CAROL_USER_ID)
                .inPaidStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create an expense with multiple items
     */
    public static Expense expenseWithMultipleItems() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .inDraftStatus()
                .withMultipleItems(3)
                .build();
    }
    
    /**
     * Create an expense in Tenant B for Eve
     */
    public static Expense expenseInTenantB() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_B)
                .withUserId(EVE_USER_ID)
                .withCreatedBy(EVE_USER_ID)
                .withUpdatedBy(EVE_USER_ID)
                .inDraftStatus()
                .withNoItems()
                .build();
    }
    
    /**
     * Create a delegated expense where Dave created it for Carol
     */
    public static Expense delegatedExpenseDaveForCarol() {
        return ExpenseTestDataBuilder.anExpense()
                .withId(EXPENSE_ID_3)
                .withTenantId(TENANT_A)
                .withUserId(CAROL_USER_ID)  // Carol is the owner
                .withCreatedBy(DAVE_USER_ID)  // Dave created it
                .withUpdatedBy(DAVE_USER_ID)
                .inDraftStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create an expense ready for submission (DRAFT with items)
     */
    public static Expense readyForSubmission() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .inDraftStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create an expense ready for approval (SUBMITTED)
     */
    public static Expense readyForApproval() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .inSubmittedStatus()
                .withMealExpenseItems()
                .build();
    }
    
    /**
     * Create a fully processed expense (APPROVED with all fields)
     */
    public static Expense fullyProcessedExpense() {
        return ExpenseTestDataBuilder.anExpense()
                .withTenantId(TENANT_A)
                .withUserId(ALICE_USER_ID)
                .withCreatedBy(ALICE_USER_ID)
                .withUpdatedBy(ALICE_USER_ID)
                .inApprovedStatus()
                .withMealExpenseItems()
                .build();
    }
    
    // ========== ExpenseItem Fixtures ==========
    
    /**
     * Create a valid meal expense item
     */
    public static ExpenseItem validMealItem() {
        return ExpenseItemTestDataBuilder.anExpenseItem()
                .withId(ITEM_ID_1)
                .asMealExpense()
                .build();
    }
    
    /**
     * Create a valid travel expense item
     */
    public static ExpenseItem validTravelItem() {
        return ExpenseItemTestDataBuilder.anExpenseItem()
                .withId(ITEM_ID_2)
                .asTravelExpense()
                .build();
    }
    
    /**
     * Create a valid accommodation expense item
     */
    public static ExpenseItem validAccommodationItem() {
        return ExpenseItemTestDataBuilder.anExpenseItem()
                .withId(ITEM_ID_3)
                .asAccommodationExpense()
                .build();
    }
    
    /**
     * Create a valid transportation expense item
     */
    public static ExpenseItem validTransportationItem() {
        return ExpenseItemTestDataBuilder.anExpenseItem()
                .asTransportationExpense()
                .build();
    }
    
    /**
     * Create multiple items with mixed categories
     */
    public static List<ExpenseItem> multipleItemsMixedCategories() {
        List<ExpenseItem> items = new ArrayList<>();
        items.add(ExpenseItemTestDataBuilder.anExpenseItem().asMealExpense().build());
        items.add(ExpenseItemTestDataBuilder.anExpenseItem().asTravelExpense().build());
        items.add(ExpenseItemTestDataBuilder.anExpenseItem().asAccommodationExpense().build());
        return items;
    }
    
    /**
     * Create items for all categories (for parameterized tests)
     */
    public static List<ExpenseItem> itemsForAllCategories() {
        List<ExpenseItem> items = new ArrayList<>();
        for (ExpenseCategory category : ExpenseCategory.values()) {
            items.add(ExpenseItemTestDataBuilder.anExpenseItem()
                    .withCategory(category)
                    .withDescription("Test " + category.name() + " expense")
                    .build());
        }
        return items;
    }
}

import { clockworkTeapot, clockworkTeapotRevised } from "../data/books.ts";
import { bookEdit } from "../pages/bookEdit.page.ts";
import { bookList } from "../pages/bookList.page.ts";

describe("book library", () => {
  // Runs first and leaves a book behind: the next test proves the data reset.
  it("keeps the book when the delete dialog is cancelled", async () => {
    await bookList.assertEmptyState();
    await bookList.tapAdd();
    await bookEdit.fillAndSave(clockworkTeapot);
    await bookList.assertBookShown(clockworkTeapot.title);

    await bookList.requestRemove(clockworkTeapot.title);
    await bookList.assertDeleteDialogOpen();
    await bookList.cancelDelete();

    await bookList.assertDeleteDialogClosed();
    await bookList.assertBookShown(clockworkTeapot.title);
  });

  it("adds, edits and deletes a book ending on the empty state", async () => {
    // The previous test left a book: the app data reset must have emptied the library.
    await bookList.assertEmptyState();
    await bookList.tapAdd();
    await bookEdit.fillAndSave(clockworkTeapot);
    await bookList.assertBookShown(clockworkTeapot.title);
    await bookList.assertBookRating(clockworkTeapot.title, clockworkTeapot.rating);

    await bookList.openBook(clockworkTeapot.title);
    await bookEdit.fillAndSave(clockworkTeapotRevised);
    await bookList.assertBookShown(clockworkTeapotRevised.title);
    await bookList.assertBookRating(clockworkTeapotRevised.title, clockworkTeapotRevised.rating);
    await bookList.assertBookAbsent(clockworkTeapot.title);

    await bookList.requestRemove(clockworkTeapotRevised.title);
    await bookList.assertDeleteDialogOpen();
    await bookList.confirmDelete();
    await bookList.assertEmptyState();
  });
});

/** Synthetic fixtures only: obviously fictional titles, authors and notes. */
export type BookFixture = {
  readonly title: string;
  readonly author: string;
  readonly rating: number;
  readonly review: string;
};

export const clockworkTeapot: BookFixture = {
  title: "The Clockwork Teapot",
  author: "A. N. Example",
  rating: 3.5,
  review: "Entirely fictional test note.",
};

export const clockworkTeapotRevised: BookFixture = {
  title: "The Clockwork Teapot Revised",
  author: "A. N. Example",
  rating: 4.5,
  review: "Entirely fictional revised note.",
};

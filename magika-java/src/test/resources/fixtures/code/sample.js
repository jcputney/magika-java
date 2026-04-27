// Minimal JavaScript fixture exercising classes, async/await, and arrow fns.
"use strict";

class Queue {
  constructor() {
    this.items = [];
  }
  push(x) {
    this.items.push(x);
  }
  pop() {
    return this.items.shift();
  }
  get size() {
    return this.items.length;
  }
}

async function drain(queue, handler) {
  while (queue.size > 0) {
    const item = queue.pop();
    await handler(item);
  }
}

const q = new Queue();
[1, 2, 3, 4, 5].forEach((n) => q.push(n * n));
drain(q, async (v) => console.log("squared:", v));

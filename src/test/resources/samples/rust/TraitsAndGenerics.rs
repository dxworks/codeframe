use std::fmt::Display;

pub trait Drawable {
    fn draw(&self);
    fn bounds(&self) -> (f64, f64, f64, f64);
}

pub trait Resizable {
    fn resize(&mut self, scale: f64);
}

pub struct Circle<T> {
    pub center_x: T,
    pub center_y: T,
    pub radius: T,
}

impl<T> Circle<T> {
    pub fn new(center_x: T, center_y: T, radius: T) -> Self {
        Circle {
            center_x,
            center_y,
            radius,
        }
    }
}

impl<T: Display> Drawable for Circle<T> {
    fn draw(&self) {
        println!("Drawing circle at ({}, {})", self.center_x, self.center_y);
    }

    fn bounds(&self) -> (f64, f64, f64, f64) {
        (0.0, 0.0, 100.0, 100.0)
    }
}

pub struct Container<T: Drawable> {
    items: Vec<T>,
}

impl<T: Drawable> Container<T> {
    pub fn new() -> Self {
        Container { items: Vec::new() }
    }

    pub fn add(&mut self, item: T) {
        self.items.push(item);
    }

    pub fn draw_all(&self) {
        for item in &self.items {
            item.draw();
        }
    }
}

pub fn process<T: Drawable + Resizable>(item: &mut T) {
    item.draw();
    item.resize(2.0);
}

pub struct Point {
    pub x: f64,
    pub y: f64,
}

impl Point {
    pub fn new(x: f64, y: f64) -> Self {
        Point { x, y }
    }

    pub fn distance(&self, other: &Point) -> f64 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        (dx * dx + dy * dy).sqrt()
    }

    pub fn translate(&mut self, dx: f64, dy: f64) {
        self.x += dx;
        self.y += dy;
    }
}

impl Default for Point {
    fn default() -> Self {
        Point { x: 0.0, y: 0.0 }
    }
}

struct Rectangle {
    top_left: Point,
    bottom_right: Point,
}

impl Rectangle {
    fn new(top_left: Point, bottom_right: Point) -> Self {
        Rectangle {
            top_left,
            bottom_right,
        }
    }

    fn area(&self) -> f64 {
        let width = (self.bottom_right.x - self.top_left.x).abs();
        let height = (self.bottom_right.y - self.top_left.y).abs();
        width * height
    }

    fn contains(&self, point: &Point) -> bool {
        point.x >= self.top_left.x
            && point.x <= self.bottom_right.x
            && point.y >= self.top_left.y
            && point.y <= self.bottom_right.y
    }
}

pub const MAX_POINTS: usize = 1000;
pub static mut POINT_COUNT: usize = 0;

import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Footer } from '../components/Footer';

describe('Footer Component', () => {
  it('renders persistent disclaimer that data is synthetic demo only', () => {
    render(<Footer />);
    expect(
      screen.getByText(/Synthetic data: demo only; no real court records are used/i)
    ).toBeInTheDocument();
  });
});
